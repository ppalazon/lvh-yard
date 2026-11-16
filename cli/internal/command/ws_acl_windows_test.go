package command_test

import (
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"unsafe"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"golang.org/x/sys/windows"
)

var createRestrictedToken = windows.NewLazySystemDLL("advapi32.dll").NewProc("CreateRestrictedToken")

// otherUserToken is an impersonation token of this process with the identity of its user, and the
// Administrators group, switched to deny only. Windows checks access with it as it does for another
// ordinary user: no ACL entry for this user or for administrators applies. The token keeps every
// other group, such as Users and Everyone, and drops every privilege except bypass of traverse checks.
func otherUserToken(t *testing.T) windows.Token {
	t.Helper()
	var process windows.Token
	if err := windows.OpenProcessToken(windows.CurrentProcess(), windows.TOKEN_DUPLICATE|windows.TOKEN_ASSIGN_PRIMARY|windows.TOKEN_QUERY, &process); err != nil {
		t.Fatal(err)
	}
	defer func() { _ = process.Close() }()
	user, err := process.GetTokenUser()
	if err != nil {
		t.Fatal(err)
	}
	disable := []windows.SIDAndAttributes{{Sid: user.User.Sid}}
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	if err != nil {
		t.Fatal(err)
	}
	groups, err := process.GetTokenGroups()
	if err != nil {
		t.Fatal(err)
	}
	for _, group := range groups.AllGroups() {
		if group.Sid.Equals(administrators) {
			disable = append(disable, windows.SIDAndAttributes{Sid: administrators})
		}
	}
	const disableMaxPrivilege = 0x1
	var restricted windows.Token
	if result, _, err := createRestrictedToken.Call(uintptr(process), disableMaxPrivilege, uintptr(len(disable)), uintptr(unsafe.Pointer(&disable[0])), 0, 0, 0, 0, uintptr(unsafe.Pointer(&restricted))); result == 0 {
		t.Fatalf("CreateRestrictedToken: %v", err)
	}
	defer func() { _ = restricted.Close() }()
	var impersonation windows.Token
	if err := windows.DuplicateTokenEx(restricted, windows.TOKEN_IMPERSONATE|windows.TOKEN_QUERY, nil, windows.SecurityImpersonation, windows.TokenImpersonation, &impersonation); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = impersonation.Close() })
	return impersonation
}

// asOtherUser runs check on this thread with the other user's token. The check only records what it
// saw: it must not fail the test, so that the thread always returns to its own identity.
func asOtherUser(t *testing.T, token windows.Token, check func()) {
	t.Helper()
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	if err := windows.SetThreadToken(nil, token); err != nil {
		t.Fatal(err)
	}
	defer func() {
		if err := windows.RevertToSelf(); err != nil {
			panic(err)
		}
	}()
	check()
}

// shareWithUsers gives the Users group read access to a file, as a folder that other users may read does.
func shareWithUsers(t *testing.T, path string) {
	t.Helper()
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		t.Fatal(err)
	}
	descriptor, err := windows.SecurityDescriptorFromString("D:P(A;;FA;;;" + user.User.Sid.String() + ")(A;;FR;;;BU)")
	if err != nil {
		t.Fatal(err)
	}
	dacl, _, err := descriptor.DACL()
	if err != nil {
		t.Fatal(err)
	}
	if err := windows.SetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION, nil, nil, dacl, nil); err != nil {
		t.Fatal(err)
	}
}

// The ACL text is not the point. This asks Windows what another ordinary user can do with the files that
// protectFile and protectRootEntry made: the owner reads them, and the other user is refused.
func TestWindowsProtectionDeniesAnotherUserByEffectiveAccess(t *testing.T) {
	yard := commandtest.New(t)
	folder := filepath.Join(t.TempDir(), "private")
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "native"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if run := yard.Run(args...); run.ExitCode != 0 {
			t.Fatalf("%v: %+v", args, run)
		}
	}
	var protected []string
	if err := filepath.WalkDir(folder, func(path string, _ fs.DirEntry, err error) error {
		protected = append(protected, path)
		return err
	}); err != nil {
		t.Fatal(err)
	}
	if len(protected) < 10 {
		t.Fatalf("too few entries to prove anything: %v", protected)
	}
	// The control proves the other token reaches files, so that every refusal below comes from the ACL.
	control := filepath.Join(t.TempDir(), "control.txt")
	if err := os.WriteFile(control, []byte("shared"), 0600); err != nil {
		t.Fatal(err)
	}
	shareWithUsers(t, control)

	token := otherUserToken(t)
	var controlErr error
	refused := map[string]error{}
	asOtherUser(t, token, func() {
		_, controlErr = os.ReadFile(control)
		for _, path := range protected {
			file, err := os.Open(path)
			if err == nil {
				_ = file.Close()
			}
			refused[path] = err
		}
	})
	if controlErr != nil {
		t.Fatalf("the other user cannot read a file that is shared with Users, so the check proves nothing: %v", controlErr)
	}
	for _, path := range protected {
		if err := refused[path]; !errors.Is(err, fs.ErrPermission) {
			t.Errorf("another user opened %s: %v", path, err)
		}
	}
	for _, path := range protected {
		file, err := os.Open(path)
		if err != nil {
			t.Errorf("the owner cannot open %s: %v", path, err)
			continue
		}
		_ = file.Close()
	}
}

func fileDACL(t *testing.T, path string) string {
	t.Helper()
	descriptor, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
	if err != nil {
		t.Fatal(err)
	}
	return descriptor.String()
}

// Windows pushes a changed folder DACL into the children that inherit it, and a hard link in the folder
// is such a child. The command may refuse the link, but it must not change the file outside the folder.
func TestWindowsProtectionLeavesAHardLinkedOutsideFileAlone(t *testing.T) {
	yard := commandtest.New(t)
	folder := filepath.Join(t.TempDir(), "private")
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "native"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if run := yard.Run(args...); run.ExitCode != 0 {
			t.Fatalf("%v: %+v", args, run)
		}
	}
	// The outside folder grants Users read to what it holds, and the outside file inherits that.
	outside := t.TempDir()
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		t.Fatal(err)
	}
	descriptor, err := windows.SecurityDescriptorFromString("D:(A;OICI;FA;;;" + user.User.Sid.String() + ")(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)(A;OICI;FR;;;BU)")
	if err != nil {
		t.Fatal(err)
	}
	dacl, _, err := descriptor.DACL()
	if err != nil {
		t.Fatal(err)
	}
	if err := windows.SetNamedSecurityInfo(outside, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION, nil, nil, dacl, nil); err != nil {
		t.Fatal(err)
	}
	victim := filepath.Join(outside, "victim.txt")
	if err := os.WriteFile(victim, []byte("victim"), 0600); err != nil {
		t.Fatal(err)
	}
	clients := filepath.Join(folder, "ca", "clients")
	if err := os.MkdirAll(clients, 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.Link(victim, filepath.Join(clients, "hard.txt")); err != nil {
		t.Fatal(err)
	}
	before := fileDACL(t, victim)

	run := yard.Run("ws", "tls", "ca", "issue", "user", "erin", "--password", "x", "--dir", folder)
	if run.ExitCode == 0 {
		t.Fatalf("a hard link in the folder was accepted: %+v", run)
	}
	if after := fileDACL(t, victim); after != before {
		t.Errorf("the file outside the folder changed its access:\nbefore %s\nafter  %s", before, after)
	}
}
