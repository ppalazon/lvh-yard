//go:build integration

package main_test

import (
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

var logonUser = windows.NewLazySystemDLL("advapi32.dll").NewProc("LogonUserW")

// otherUserToken logs on a second account of this computer and returns an impersonation token.
func otherUserToken(t *testing.T, name, password string) windows.Token {
	t.Helper()
	user, err := windows.UTF16PtrFromString(name)
	if err != nil {
		t.Fatal(err)
	}
	domain, _ := windows.UTF16PtrFromString(".")
	secret, err := windows.UTF16PtrFromString(password)
	if err != nil {
		t.Fatal(err)
	}
	const logon32LogonInteractive, logon32ProviderDefault = 2, 0
	var primary windows.Token
	if result, _, err := logonUser.Call(uintptr(unsafe.Pointer(user)), uintptr(unsafe.Pointer(domain)), uintptr(unsafe.Pointer(secret)),
		logon32LogonInteractive, logon32ProviderDefault, uintptr(unsafe.Pointer(&primary))); result == 0 {
		t.Fatalf("could not log on %s: %v", name, err)
	}
	defer func() { _ = primary.Close() }()
	var impersonation windows.Token
	if err := windows.DuplicateTokenEx(primary, windows.TOKEN_IMPERSONATE|windows.TOKEN_QUERY, nil, windows.SecurityImpersonation, windows.TokenImpersonation, &impersonation); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = impersonation.Close() })
	return impersonation
}

// A real second logon, not an ACL reading. The owner opens every file that ws init and ws tls ca create
// make, and the other account is refused every one. A control file that Users may read shows that the
// other account reaches files at all, so each refusal comes from the folder's own ACL.
//
// Run it as the owner, with a second ordinary account of this computer:
//
//	YARD_TEST_OTHER_USER=yardother YARD_TEST_OTHER_PASSWORD=... go test -tags integration -run ProtectsTheWorkstationFolder
func TestBuiltExecutableProtectsTheWorkstationFolderFromAnotherUser(t *testing.T) {
	name, password := os.Getenv("YARD_TEST_OTHER_USER"), os.Getenv("YARD_TEST_OTHER_PASSWORD")
	if name == "" || password == "" {
		t.Skip("set YARD_TEST_OTHER_USER and YARD_TEST_OTHER_PASSWORD to a second ordinary account to log on as it")
	}
	folder := filepath.Join(t.TempDir(), "private")
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "native", "--base-domain", "lvh.me", "--address", "192.168.1.20"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if got := run(t, nil, args...); got.code != 0 {
			t.Fatalf("%v: %+v", args, got)
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
	control := filepath.Join(t.TempDir(), "control.txt")
	if err := os.WriteFile(control, []byte("shared"), 0600); err != nil {
		t.Fatal(err)
	}
	shareWithUsers(t, control)

	token := otherUserToken(t, name, password)
	var controlErr error
	refused := map[string]error{}
	runtime.LockOSThread()
	if err := windows.SetThreadToken(nil, token); err != nil {
		runtime.UnlockOSThread()
		t.Fatal(err)
	}
	_, controlErr = os.ReadFile(control)
	for _, path := range protected {
		file, err := os.Open(path)
		if err == nil {
			_ = file.Close()
		}
		refused[path] = err
	}
	if err := windows.RevertToSelf(); err != nil {
		panic(err)
	}
	runtime.UnlockOSThread()

	if controlErr != nil {
		t.Fatalf("%s cannot read a file that is shared with Users, so the check proves nothing: %v", name, controlErr)
	}
	for _, path := range protected {
		if err := refused[path]; !errors.Is(err, fs.ErrPermission) {
			t.Errorf("%s opened %s: %v", name, path, err)
		}
		file, err := os.Open(path)
		if err != nil {
			t.Errorf("the owner cannot open %s: %v", path, err)
			continue
		}
		_ = file.Close()
	}
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
