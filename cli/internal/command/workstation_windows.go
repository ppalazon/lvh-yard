package command

import (
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"unsafe"

	"golang.org/x/sys/windows"
)

func defaultWorkstationFolder(process Process) string {
	local := process.Getenv("LOCALAPPDATA")
	if strings.TrimSpace(local) == "" {
		home, _ := os.UserHomeDir()
		local = filepath.Join(home, "AppData", "Local")
	}
	return filepath.Join(local, "lvh-yard", "workstation")
}

func validateOutputName(name string) error {
	invalid := name == "" || strings.HasSuffix(name, ".") || strings.HasSuffix(name, " ")
	for _, r := range name {
		invalid = invalid || r < 32 || strings.ContainsRune(`<>:"/\|?*`, r)
	}
	base := strings.ToUpper(strings.SplitN(name, ".", 2)[0])
	invalid = invalid || base == "CON" || base == "PRN" || base == "AUX" || base == "NUL" || base == "CONIN$" || base == "CONOUT$"
	if len([]rune(base)) == 4 && (strings.HasPrefix(base, "COM") || strings.HasPrefix(base, "LPT")) {
		invalid = invalid || strings.ContainsRune("123456789¹²³", []rune(base)[3])
	}
	if invalid {
		return fmt.Errorf("invalid Windows filename %q: use a name without reserved names, control characters, <>:\"/\\|?*, or a trailing dot or space", name)
	}
	return nil
}

func validateOutputPath(path string) error {
	for _, name := range strings.FieldsFunc(strings.TrimPrefix(path, filepath.VolumeName(path)), func(r rune) bool { return r == '/' || r == '\\' }) {
		if name == "." || name == ".." {
			continue
		}
		if err := validateOutputName(name); err != nil {
			return err
		}
	}
	return nil
}

func validateWorkstationStorage(folder string) error {
	if err := validateOutputPath(folder); err != nil {
		return err
	}
	absolute, err := filepath.Abs(folder)
	if err != nil {
		return err
	}
	volume := filepath.VolumeName(absolute)
	if len(volume) != 2 || volume[1] != ':' {
		return fmt.Errorf("unsupported Workstation Folder: choose local NTFS storage, not a network share or synced folder")
	}
	if err := validateLocalNTFS(volume + `\`); err != nil {
		return err
	}
	current := volume + `\`
	for name := range strings.SplitSeq(strings.TrimPrefix(absolute, current), `\`) {
		if isSyncedFolderName(name) {
			return fmt.Errorf("unsupported synced Workstation Folder: choose local NTFS storage outside cloud-sync folders")
		}
		current = filepath.Join(current, name)
		if err := validateNotLinked(current); err != nil {
			return err
		}
	}
	return nil
}

// validateLocalNTFS accepts only a fixed drive with an NTFS volume, as the private ACLs need it.
func validateLocalNTFS(volumeRoot string) error {
	root, err := windows.UTF16PtrFromString(volumeRoot)
	if err != nil {
		return err
	}
	if windows.GetDriveType(root) != windows.DRIVE_FIXED {
		return fmt.Errorf("unsupported Workstation Folder: choose a local fixed NTFS drive, not shared or synced storage")
	}
	var filesystem [64]uint16
	if err := windows.GetVolumeInformation(root, nil, 0, nil, nil, nil, &filesystem[0], uint32(len(filesystem))); err != nil {
		return fmt.Errorf("could not check Workstation storage: %w", err)
	}
	if !strings.EqualFold(windows.UTF16ToString(filesystem[:]), "NTFS") {
		return fmt.Errorf("unsupported Workstation Folder: choose local NTFS storage")
	}
	return nil
}

func isSyncedFolderName(name string) bool {
	lower := strings.ToLower(name)
	return strings.HasPrefix(lower, "onedrive") || lower == "dropbox" || lower == "google drive" || lower == "iclouddrive"
}

// validateNotLinked refuses a link or junction. A path that does not exist yet is fine.
func validateNotLinked(path string) error {
	name, err := windows.UTF16PtrFromString(path)
	if err != nil {
		return err
	}
	attributes, err := windows.GetFileAttributes(name)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("could not check Workstation Folder %s: %w", path, err)
	}
	if attributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0 {
		return fmt.Errorf("unsupported Workstation Folder %s: use a local NTFS folder without links, junctions or cloud sync", path)
	}
	return nil
}

func validateNativeListener(name, value string) error {
	if name == "bind-address" {
		ip := net.ParseIP(value)
		if !isGlobalUnicastIPv4(ip) {
			return fmt.Errorf("invalid Windows bind-address: use the LAN IPv4 address that other Workstations can reach")
		}
	}
	if (name == "dashboard-bind-address" || name == "traefik-bind-address") && value != "127.0.0.1" && value != "0.0.0.0" {
		return fmt.Errorf("unsupported Windows %s: Docker Desktop supports 127.0.0.1 or 0.0.0.0, not a native interface address", name)
	}
	return nil
}

func protectFile(file *os.File, _ os.FileMode) error {
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		return err
	}
	// Inherit only the current user's, SYSTEM's and administrators' access. Docker Desktop reads as this user.
	descriptor, err := windows.SecurityDescriptorFromString("D:P(A;OICI;FA;;;" + user.User.Sid.String() + ")(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)")
	if err != nil {
		return err
	}
	// Every entry of the folder is protected by itself. SetSecurityInfo would also push this DACL
	// into children, which includes a hard link to a file outside the folder.
	handle, err := reopenSecurityHandle(file)
	if err != nil {
		return fmt.Errorf("could not reopen the rooted file for ACL access: %w", err)
	}
	defer func() { _ = windows.CloseHandle(handle) }()
	if err := windows.SetKernelObjectSecurity(handle, windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION, descriptor); err != nil {
		return fmt.Errorf("could not set the private DACL: %w", err)
	}
	return nil
}

func reopenSecurityHandle(file *os.File) (windows.Handle, error) {
	// An empty NT name reopens this object, not a path that can be swapped. ReOpenFile denies directory ACL access.
	empty, err := windows.NewNTUnicodeString("")
	if err != nil {
		return windows.InvalidHandle, err
	}
	attributes := windows.OBJECT_ATTRIBUTES{RootDirectory: windows.Handle(file.Fd()), ObjectName: empty}
	attributes.Length = uint32(unsafe.Sizeof(attributes))
	var handle windows.Handle
	var status windows.IO_STATUS_BLOCK
	err = windows.NtCreateFile(&handle, windows.WRITE_DAC|windows.READ_CONTROL|windows.SYNCHRONIZE,
		&attributes, &status, nil, 0, windows.FILE_SHARE_READ|windows.FILE_SHARE_WRITE|windows.FILE_SHARE_DELETE,
		windows.FILE_OPEN, windows.FILE_OPEN_FOR_BACKUP_INTENT|windows.FILE_SYNCHRONOUS_IO_NONALERT, 0, 0)
	return handle, err
}

func protectRootEntry(root *os.Root, name string, mode os.FileMode) error {
	file, err := root.Open(name)
	if err != nil {
		return err
	}
	defer func() { _ = file.Close() }()
	var info windows.ByHandleFileInformation
	if err := windows.GetFileInformationByHandle(windows.Handle(file.Fd()), &info); err != nil {
		return err
	}
	if info.NumberOfLinks > 1 && info.FileAttributes&windows.FILE_ATTRIBUTE_DIRECTORY == 0 {
		return fmt.Errorf("cannot protect a file with multiple hard links: remove the link from the Workstation Folder")
	}
	return protectFile(file, mode)
}
