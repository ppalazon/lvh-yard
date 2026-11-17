package command

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
	"github.com/spf13/cobra"
)

// exportKind is one kind of zip file that ws tls ca export makes. Neither holds a private key
// without a password of its own, so neither zip file is encrypted: a PKCS#12 file has its own
// password, and a CA certificate is public. The Consul certificates travel in the encrypted
// invitation of ws yard share, and a Traefik certificate never leaves its Workstation.
type exportKind struct {
	use, short string
	// name asks for the name that the zip file is for. It is nil when the zip file has no name.
	name    func(p *prompter, args []string, self string) (string, error)
	zipName func(name string) string
	// files reads what this Workstation's CA signed. ws is the command to print in an error.
	files func(folder, ws, name string, caPEM []byte) ([]certzip.File, error)
	note  string
}

// newWSTLSExport makes zip files to send by email, with the steps to use them.
func newWSTLSExport(options *Options, folder func() string) *cobra.Command {
	export := &cobra.Command{Use: "export", Short: "Packs certificates in a zip file to send.", Args: cobra.NoArgs}
	for _, kind := range []exportKind{userExport, caExport} {
		export.AddCommand(newWSTLSExportCommand(options, folder, kind))
	}
	return export
}

func newWSTLSExportCommand(options *Options, folder func() string, kind exportKind) *cobra.Command {
	var output string
	args := cobra.NoArgs
	if kind.name != nil {
		args = cobra.MaximumNArgs(1)
	}
	cmd := &cobra.Command{Use: kind.use, Short: kind.short, Args: args, RunE: func(cmd *cobra.Command, args []string) error {
		return runExport(cmd, options, folder(), kind, args, output)
	}}
	cmd.Flags().StringVarP(&output, "output", "o", "", "Writes the zip file here (default: certificates/ in the Workstation Folder).")
	return cmd
}

func runExport(cmd *cobra.Command, options *Options, folder string, kind exportKind, args []string, output string) error {
	env, err := readWSEnv(folder)
	if err != nil {
		return err
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	caPEM, err := readCAPEM(folder)
	if err != nil {
		return err
	}
	var name string
	if kind.name != nil {
		if name, err = kind.name(newPrompter(cmd, options), args, strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")); err != nil {
			return err
		}
	}
	files, err := kind.files(folder, wsCommand(folder, options.WorkstationFolder()), name, caPEM)
	if err != nil {
		return err
	}
	data, err := certzip.Build(files, "")
	if err != nil {
		return err
	}
	path, err := writeExport(cmd, folder, output, kind.zipName(name), data)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	fmt.Fprintf(out, "Zip file saved in %s.\n  Holds:", path)
	for _, file := range files {
		fmt.Fprintf(out, " %s", file.Name)
	}
	fmt.Fprintf(out, "\n%s\n", kind.note)
	return nil
}

// writeExport writes in certificates/ of the Workstation Folder, unless the user chose a file.
func writeExport(cmd *cobra.Command, folder, output, zipName string, data []byte) (string, error) {
	if cmd.Flags().Changed("output") {
		if err := writePrivateFile(output, data); err != nil {
			return "", fmt.Errorf("Cannot write %s: %w", output, err)
		}
		return output, nil
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return "", err
	}
	defer func() { _ = root.Close() }()
	if err := root.MkdirAll("certificates", 0700); err != nil {
		return "", err
	}
	rel := filepath.Join("certificates", zipName)
	if err := root.WriteFile(rel, data, 0600); err != nil {
		return "", err
	}
	return filepath.Join(folder, rel), protectWorkstationFolder(folder)
}

var userExport = exportKind{
	use:   "user [username-or-email]",
	short: "Packs the Client Certificate of a user.",
	name: func(p *prompter, args []string, _ string) (string, error) {
		return askTLSClientName(p, args)
	},
	zipName: func(name string) string { return name + "-user.zip" },
	note:    "Next:\n  Send the password of the .p12 file by another way than email.",
	files: func(folder, ws, name string, caPEM []byte) ([]certzip.File, error) {
		root, err := os.OpenRoot(folder)
		if err != nil {
			return nil, err
		}
		defer func() { _ = root.Close() }()
		p12, err := root.ReadFile(filepath.Join("ca", "clients", name+".p12"))
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("this Workstation's CA has signed no Client Certificate for %s. Run %s tls ca issue user %s", name, ws, quoteShell(name))
		}
		if err != nil {
			return nil, err
		}
		return []certzip.File{{Name: name + ".p12", Data: p12}, {Name: "ca.crt", Data: caPEM}, {Name: "README.txt", Data: []byte(userReadme(name))}}, nil
	},
}

var caExport = exportKind{
	use:     "ca",
	short:   "Packs the Workstation CA certificate.",
	zipName: func(string) string { return "ca.zip" },
	note:    "Note:\n  - The zip file holds only the public CA certificate.",
	files: func(_, _, _ string, caPEM []byte) ([]certzip.File, error) {
		return []certzip.File{{Name: "ca.crt", Data: caPEM}, {Name: "README.txt", Data: []byte(caReadme)}}, nil
	},
}

const trustSteps = `To trust the HTTPS Routes of the sender:
  1. Import ca.crt as a certificate authority for websites.
  2. Firefox: Settings, Privacy & Security, Certificates, View Certificates, Authorities, Import.
  3. Browsers that use the system store: install ca.crt there.
`

const trustNote = "  - Import only ca.crt. Your browser then trusts every certificate that its CA signs.\n"

func userReadme(name string) string {
	return fmt.Sprintf(`Client Certificate of %[1]s

Files:
  %[1]s.p12  The private key, the Client Certificate and the CA certificate.
  ca.crt     The CA certificate.

Before:
  - Get the password of the .p12 file from the sender, by another way.

Steps:
  1. Firefox: Settings, Privacy & Security, Certificates, View Certificates, Your Certificates, Import.
  2. Select %[1]s.p12 and enter its password.
  3. Other browsers: import the .p12 file into the personal certificates.
  4. When a Route asks for a Client Certificate, select the one for %[1]s.

%[2]s
Note:
%[3]s  - Keep the .p12 file and its password private.
  - Delete this email and the zip file after the import.
`, name, trustSteps, trustNote)
}

var caReadme = "Certificate of a Workstation CA\n\nFile:\n  ca.crt  The CA certificate. It holds no private key.\n\n" + trustSteps + "\nNote:\n" + trustNote
