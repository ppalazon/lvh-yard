package command

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
)

// installRole checks a certificate, copies it to tls/<role>/ and turns the role on. The caller
// saves .env and prints the apply command with finishRoles.
func installRole(cmd *cobra.Command, folder string, env *envFile, role string, caPEM, certPEM, keyPEM []byte) error {
	if err := checkRoleCertificate(role, env, caPEM, certPEM, keyPEM); err != nil {
		return err
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	dir := roleDir(folder, role)
	if err := writeRole(dir, role, caPEM, certPEM, keyPEM); err != nil {
		return err
	}
	if err := turnOnRole(folder, role, env); err != nil {
		return err
	}
	fmt.Fprintf(cmd.OutOrStdout(), "%s certificate installed in %s.\n", roleSettingName(role), dir)
	announceRole(cmd, role)
	return nil
}

func newWSTLSImport(options *Options, folder func() string) *cobra.Command {
	var caFile, certFile, keyFile string
	cmd := &cobra.Command{
		Use: "import <consul|traefik>", Short: "Installs a certificate and turns its service on: one that another CA owner signed, or a public one.",
		Long: `Installs a certificate for Consul or for Traefik, and turns that service on.

For Consul, give the Yard CA and a certificate that it signed, with the name
server.<this Workstation>.consul and both server and client usage.

For Traefik, give a certificate for HTTPS: from the Yard CA, a company CA or a public CA such
as Let's Encrypt. It must name yard.<Base Domain>. --ca is always the Yard CA, which Traefik
uses to check Client Certificates.`,
		Args: serviceArgument, ValidArgs: tlsRoles,
		RunE: func(cmd *cobra.Command, args []string) error {
			role := args[0]
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if role == roleTraefik && traefikOff(env) {
				return fmt.Errorf("Traefik is off on this Workstation: turn it on with lvh-yard ws set traefik on")
			}
			caPEM, err := os.ReadFile(caFile)
			if err != nil {
				return fmt.Errorf("read CA: %w", err)
			}
			certPEM, err := os.ReadFile(certFile)
			if err != nil {
				return fmt.Errorf("read certificate: %w", err)
			}
			keyPEM, err := os.ReadFile(keyFile)
			if err != nil {
				return fmt.Errorf("read key: %w", err)
			}
			if err := installRole(cmd, folder(), env, role, caPEM, certPEM, keyPEM); err != nil {
				return err
			}
			if err := finishRoles(cmd, folder(), env, []string{role}); err != nil {
				return err
			}
			return protectWorkstationFolder(folder())
		},
	}
	cmd.Flags().StringVar(&caFile, "ca", "", "Reads the Yard CA certificate.")
	cmd.Flags().StringVar(&certFile, "cert", "", "Reads this certificate.")
	cmd.Flags().StringVar(&keyFile, "key", "", "Reads this certificate key.")
	for _, name := range []string{"ca", "cert", "key"} {
		_ = cmd.MarkFlagRequired(name)
	}
	return cmd
}

// newWSTLSInstall puts certificates that this Workstation's Yard CA signed where they belong: in
// tls/ for this Workstation, or in folders to copy for another one.
func newWSTLSInstall(options *Options, folder func() string) *cobra.Command {
	var services tlsServices
	var remote string
	cmd := &cobra.Command{Use: "install [name]", Short: "Installs the certificates that the Yard CA signed, here or in folders to copy.", Args: cobra.MaximumNArgs(1), RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		self := strings.Trim(env.get("YARD_WORKSTATION"), "\"'")
		name := self
		p := newPrompter(cmd, options)
		if len(args) == 1 {
			name = args[0]
		} else if p.terminal {
			if name, err = p.text("Workstation name, as it is in the Yard", self, checkWorkstationName); err != nil {
				return err
			}
		}
		if err := checkWorkstationName(name); err != nil {
			return err
		}
		_, _, caPEM, err := readYardCA(folder())
		if err != nil {
			return err
		}
		ws := wsCommand(folder(), options.WorkstationFolder())
		signed := map[string][2][]byte{}
		var available []string
		for _, role := range tlsRoles {
			certPEM, certErr := os.ReadFile(issuedPath(folder(), name, role, ".crt"))
			keyPEM, keyErr := os.ReadFile(issuedPath(folder(), name, role, ".key"))
			if certErr == nil && keyErr == nil {
				signed[role] = [2][]byte{certPEM, keyPEM}
				available = append(available, role)
			}
		}
		if len(available) == 0 {
			return fmt.Errorf("the Yard CA has signed nothing for %s: run %s tls ca issue workstation %s", name, ws, quoteShell(name))
		}
		checkEnv := env
		if name != self {
			checkEnv = nil
			// First, because it is about where the files go, not which ones.
			if !cmd.Flags().Changed("scp") && p.terminal {
				if remote, err = p.text("Copy the files into its Workstation Folder with scp? Give user@host or user@host:folder, or leave empty to copy them yourself", "", checkSCPHost); err != nil {
					return err
				}
			}
		}
		roles, err := services.choose(cmd, p, checkEnv, available, name != self)
		if err != nil {
			return err
		}
		if len(roles) == 0 {
			fmt.Fprintln(cmd.OutOrStdout(), "Nothing was installed.")
			return nil
		}
		if name == self {
			for _, role := range roles {
				if err := installRole(cmd, folder(), env, role, caPEM, signed[role][0], signed[role][1]); err != nil {
					return err
				}
			}
			if err := finishRoles(cmd, folder(), env, roles); err != nil {
				return err
			}
			return protectWorkstationFolder(folder())
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		root := bundleRoot(folder(), name)
		for _, role := range roles {
			if err := writeRole(filepath.Join(root, role), role, caPEM, signed[role][0], signed[role][1]); err != nil {
				return err
			}
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		return handOverBundle(cmd, options, name, root, roles, remote)
	}}
	cmd.Flags().StringVar(&remote, "scp", "", "Copies the files of another Workstation into its tls folder with scp: user@host or user@host:folder.")
	addTLSServiceFlags(cmd, &services)
	return cmd
}

// remoteFolderDefault is where ws init puts a Workstation Folder, from the home directory of the
// user that ssh logs in as.
const remoteFolderDefault = ".config/lvh-yard"

// splitSCPTarget reads user@host or user@host:folder.
func splitSCPTarget(target string) (host, folder string, err error) {
	host, folder, _ = strings.Cut(target, ":")
	if host == "" || strings.HasPrefix(host, "-") || strings.ContainsAny(target, " \t\n'\"") || strings.HasPrefix(folder, "~") {
		return "", "", fmt.Errorf("Use user@host, or user@host:folder with a folder that is absolute or relative to the home directory.")
	}
	if folder == "" {
		folder = remoteFolderDefault
	}
	return host, folder, nil
}

func checkSCPHost(target string) error {
	if target == "" {
		return nil
	}
	_, _, err := splitSCPTarget(target)
	return err
}

// handOverBundle tells how to get the folders of another Workstation into the tls/ folder of its
// Workstation Folder, and copies them first when asked. tls/ is made private first, because the
// keys go into it. The other Workstation then turns on what it needs with ws tls enable, which
// checks each certificate first.
func handOverBundle(cmd *cobra.Command, options *Options, name, root string, roles []string, target string) error {
	out := cmd.OutOrStdout()
	if err := checkSCPHost(target); err != nil {
		return fmt.Errorf("--scp: %w", err)
	}
	fmt.Fprintf(out, "Files for %s saved in %s:\n", name, root)
	var dirs []string
	for _, role := range roles {
		files := roleFiles(role)
		fmt.Fprintf(out, "  %s/: %s, %s and %s\n", role, files[0], files[1], files[2])
		dirs = append(dirs, filepath.Join(root, role))
	}
	fmt.Fprintln(out, "The .key files are private. Send them only through a private channel, and never send ca.key.")
	host, folder := "<user@host>", remoteFolderDefault
	if target != "" {
		host, folder, _ = splitSCPTarget(target)
		for _, step := range []struct {
			name string
			args []string
		}{
			{"ssh", []string{host, "umask 077 && mkdir -p " + quoteShell(folder+"/tls")}},
			{"scp", append(append([]string{"-r"}, dirs...), host+":"+folder+"/tls/")},
		} {
			code, err := options.Process.Run(step.name, step.args, "", out, cmd.ErrOrStderr())
			if errors.Is(err, exec.ErrNotFound) {
				return fmt.Errorf("%s is not on the PATH: copy the files yourself, as described in docs/user-guide.md", step.name)
			}
			if err != nil {
				return err
			}
			if code != 0 {
				return fmt.Errorf("%s failed with exit code %d: copy the files yourself, as described in docs/user-guide.md", step.name, code)
			}
		}
		fmt.Fprintf(out, "Copied the folders to %s:%s/tls/.\n", host, folder)
	}
	ws := "lvh-yard ws"
	if folder != remoteFolderDefault {
		ws += " --dir " + quoteShell(folder)
	}
	quotedDirs := make([]string, len(dirs))
	for i, dir := range dirs {
		quotedDirs[i] = quoteShell(dir)
	}
	if target == "" {
		fmt.Fprintf(out, "\nIf %[1]s runs on another computer, copy the folders into the tls folder of its Workstation Folder (%[2]s on it unless you chose another):\n  ssh %[3]s 'umask 077 && mkdir -p %[2]s/tls'\n  scp -r %[4]s %[3]s:%[2]s/tls/\nTo do this from here, run this command again with --scp %[3]s, or --scp %[3]s:<its-folder>.\n", name, folder, host, strings.Join(quotedDirs, " "))
	}
	fmt.Fprintf(out, "\nThen, on %s, turn on what it needs. Each command checks the certificate first:\n  ssh %s\n", name, host)
	for _, role := range roles {
		fmt.Fprintf(out, "  %s tls enable %s\n", ws, role)
	}
	if target == "" {
		fmt.Fprintf(out, "\nIf %s runs on this computer, import from the folders, with its own Workstation Folder:\n", name)
		for _, role := range roles {
			dir := filepath.Join(root, role)
			fmt.Fprintf(out, "  lvh-yard ws --dir '<%s-workstation-folder>' tls import %s --ca %s --cert %s --key %s\n", name, role, quoteShell(filepath.Join(dir, "ca.crt")), quoteShell(filepath.Join(dir, role+".crt")), quoteShell(filepath.Join(dir, role+".key")))
		}
	}
	return nil
}
