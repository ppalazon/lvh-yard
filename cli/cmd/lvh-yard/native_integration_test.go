//go:build integration

package main_test

import (
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"runtime"
	"strconv"
	"strings"
	"testing"
	"unicode/utf16"
)

// These tests run the executable with a real editor process, a real docker process and the real
// shell of this system. The editor and docker are copies of this test binary, so they are native
// executables on each system and need no Unix tool.

const (
	standInRole   = "YARD_TEST_ROLE"
	standInRecord = "YARD_TEST_RECORD"
	standInExit   = "YARD_TEST_EXIT"
	// awkwardName has a space, an apostrophe, a typographic apostrophe and letters outside ASCII.
	awkwardName = "Yard d'été \u201924 日本"
)

// start is what a stand-in records about its own start.
type start struct {
	Args []string
	Dir  string
}

// actAs runs this test binary as the editor or as docker. It records its arguments and folder in
// the file that YARD_TEST_RECORD names, and exits with YARD_TEST_EXIT.
func actAs(role string) int {
	dir, err := os.Getwd()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 90
	}
	record, err := json.Marshal(start{Args: os.Args[1:], Dir: dir})
	if err == nil {
		err = os.WriteFile(os.Getenv(standInRecord), record, 0600)
	}
	if err == nil && role == "editor" {
		path := os.Args[len(os.Args)-1]
		var text []byte
		if text, err = os.ReadFile(path); err == nil {
			err = os.WriteFile(path, []byte(strings.ReplaceAll(string(text), "X-Team: a", "X-Team: b")), 0600)
		}
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 90
	}
	code, _ := strconv.Atoi(os.Getenv(standInExit))
	return code
}

// standIn copies this test binary to folder under the given name. It returns the path of the copy,
// the environment that makes the copy act as role, and a function that reads what it recorded.
func standIn(t *testing.T, folder, name, role string) (string, []string, func() start) {
	t.Helper()
	self, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	source, err := os.Open(self)
	if err != nil {
		t.Fatal(err)
	}
	defer source.Close()
	path := filepath.Join(folder, executableName(name))
	target, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0700)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := io.Copy(target, source); err != nil {
		t.Fatal(err)
	}
	if err := target.Close(); err != nil {
		t.Fatal(err)
	}
	record := filepath.Join(t.TempDir(), "start.json")
	recorded := func() start {
		t.Helper()
		var got start
		text, err := os.ReadFile(record)
		if err != nil {
			t.Fatalf("%s did not start: %v", name, err)
		}
		if err := json.Unmarshal(text, &got); err != nil {
			t.Fatal(err)
		}
		return got
	}
	return path, []string{standInRole + "=" + role, standInRecord + "=" + record}, recorded
}

func awkwardFolder(t *testing.T) string {
	t.Helper()
	folder := filepath.Join(t.TempDir(), awkwardName)
	if err := os.Mkdir(folder, 0700); err != nil {
		t.Fatal(err)
	}
	return folder
}

func pathWith(folders ...string) string {
	return "PATH=" + strings.Join(append(folders, os.Getenv("PATH")), string(os.PathListSeparator))
}

// profileDashboard serves the Route Profile team and counts the saves that hold the edited text.
func profileDashboard(t *testing.T, saves *int) *httptest.Server {
	t.Helper()
	dashboard := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/workstation/route-profiles/team" {
			http.NotFound(w, r)
			return
		}
		if r.Method == http.MethodPut {
			var got any
			_ = json.NewDecoder(r.Body).Decode(&got)
			want := map[string]any{"headers": map[string]any{"customRequestHeaders": map[string]any{"X-Team": "b"}}}
			if !reflect.DeepEqual(got, want) {
				t.Errorf("saved profile: %#v", got)
			}
			*saves++
		}
		fmt.Fprint(w, `{"name":"team","definition":{"headers":{"customRequestHeaders":{"X-Team":"a"}}}}`)
	}))
	t.Cleanup(dashboard.Close)
	return dashboard
}

// temporaryFolder makes the executable write its temporary files to an awkward folder.
func temporaryFolder(t *testing.T) (string, []string) {
	t.Helper()
	folder := awkwardFolder(t)
	return folder, []string{"TMPDIR=" + folder, "TMP=" + folder, "TEMP=" + folder}
}

func keptProfiles(t *testing.T, folder string) []string {
	t.Helper()
	kept, err := filepath.Glob(filepath.Join(folder, "route-profile-*.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	return kept
}

func TestBuiltExecutableSavesWhatTheEditorWrote(t *testing.T) {
	type editorCase struct {
		name string
		// editor makes the EDITOR value from the path of the editor.
		editor   func(path string) string
		wantArgs []string
	}
	cases := []editorCase{
		{"quoted path with an argument", func(path string) string { return `"` + path + `" --wait` }, []string{"--wait"}},
		{"name on the PATH with an argument", func(string) string { return "my-editor --wait" }, []string{"--wait"}},
	}
	if runtime.GOOS == "windows" {
		// On Unix, EDITOR is shell text, so a path with spaces needs quotes there.
		cases = append(cases, editorCase{"path with spaces and no quotes", func(path string) string { return path }, []string{}})
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			saves := 0
			dashboard := profileDashboard(t, &saves)
			editorFolder := awkwardFolder(t)
			editor, env, recorded := standIn(t, editorFolder, "my-editor", "editor")
			temporary, temporaryEnv := temporaryFolder(t)
			env = append(env, temporaryEnv...)
			env = append(env, "EDITOR="+c.editor(editor), pathWith(editorFolder))

			got := run(t, env, "--dashboard", dashboard.URL, "profiles", "edit", "team")

			if got.code != 0 || !strings.Contains(got.stdout, "Saved the Route Profile team.") || got.stderr != "" || saves != 1 {
				t.Fatalf("edit: %+v, saves %d", got, saves)
			}
			args := recorded().Args
			if len(args) != len(c.wantArgs)+1 || !reflect.DeepEqual(args[:len(c.wantArgs)], c.wantArgs) {
				t.Fatalf("editor arguments: %q", args)
			}
			if file := args[len(args)-1]; filepath.Dir(file) != temporary {
				t.Errorf("the editor got the file %q, outside %q", file, temporary)
			}
			if kept := keptProfiles(t, temporary); len(kept) != 0 {
				t.Errorf("temporary files stay: %q", kept)
			}
		})
	}
}

func TestBuiltExecutableKeepsTheTextWhenTheEditorFails(t *testing.T) {
	for _, c := range []struct {
		name string
		// missing names an editor that does not exist.
		missing  bool
		wantText string
	}{
		{"editor does not exist", true, "X-Team: a"},
		{"editor exits with an error after a change", false, "X-Team: b"},
	} {
		t.Run(c.name, func(t *testing.T) {
			saves := 0
			dashboard := profileDashboard(t, &saves)
			editorFolder := awkwardFolder(t)
			editor, env, _ := standIn(t, editorFolder, "my-editor", "editor")
			if c.missing {
				editor = filepath.Join(editorFolder, executableName("no-such-editor"))
			}
			temporary, temporaryEnv := temporaryFolder(t)
			env = append(env, temporaryEnv...)
			env = append(env, `EDITOR="`+editor+`"`, standInExit+"=3")

			got := run(t, env, "--dashboard", dashboard.URL, "profiles", "edit", "team")

			kept := keptProfiles(t, temporary)
			if got.code != 1 || got.stdout != "" || saves != 0 || len(kept) != 1 {
				t.Fatalf("edit: %+v, saves %d, kept %q", got, saves, kept)
			}
			if !strings.Contains(got.stderr, kept[0]) {
				t.Errorf("the error does not name %q: %s", kept[0], got.stderr)
			}
			text, err := os.ReadFile(kept[0])
			if err != nil || !strings.Contains(string(text), c.wantText) {
				t.Errorf("kept text: %q, %v", text, err)
			}
		})
	}
}

func TestBuiltExecutableGivesDockerItsArgumentsAndFolder(t *testing.T) {
	folder := awkwardFolder(t)
	bin := t.TempDir()
	_, env, recorded := standIn(t, bin, "docker", "docker")
	env = append(env, "PATH="+bin, standInExit+"=7")

	got := run(t, env, "ws", "--dir", folder, "compose", "logs", "-f", "consul")

	if got.code != 7 {
		t.Fatalf("exit code of docker is lost: %+v", got)
	}
	want := start{Args: []string{"compose", "--project-directory", folder, "logs", "-f", "consul"}, Dir: folder}
	if docker := recorded(); !reflect.DeepEqual(docker, want) {
		t.Fatalf("docker started as %+v, want %+v", docker, want)
	}
}

func TestBuiltExecutableReportsMissingDocker(t *testing.T) {
	got := run(t, []string{"PATH=" + t.TempDir()}, "ws", "--dir", awkwardFolder(t), "compose", "stop")
	if got.code != 1 || !strings.Contains(got.stderr, "docker") {
		t.Fatalf("missing docker: %+v", got)
	}
}

func TestBuiltExecutableAsksNothingWithoutATerminal(t *testing.T) {
	folder := filepath.Join(t.TempDir(), "not-created")
	got := run(t, nil, "ws", "--dir", folder, "init")
	if got.code != 1 || !strings.Contains(got.stderr, "--workstation") {
		t.Fatalf("init without a terminal: %+v", got)
	}
	if _, err := os.Stat(folder); !os.IsNotExist(err) {
		t.Fatalf("init wrote without an answer: %v", err)
	}
}

// shells are the ways to run one printed command line in each shell of this system.
func shells(t *testing.T) map[string]func(line string) *exec.Cmd {
	t.Helper()
	if runtime.GOOS != "windows" {
		return map[string]func(string) *exec.Cmd{"sh": func(line string) *exec.Cmd { return exec.Command("sh", "-c", line) }}
	}
	found := map[string]func(string) *exec.Cmd{}
	for _, shell := range []string{"powershell.exe", "pwsh.exe"} {
		path, err := exec.LookPath(shell)
		if err != nil {
			t.Logf("%s is not on the PATH, so it is not checked", shell)
			continue
		}
		found[shell] = func(line string) *exec.Cmd {
			// -EncodedCommand takes UTF-16, so the line reaches the parser as pasted text does.
			units := utf16.Encode([]rune(line))
			raw := make([]byte, 2*len(units))
			for i, unit := range units {
				binary.LittleEndian.PutUint16(raw[2*i:], unit)
			}
			return exec.Command(path, "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", base64.StdEncoding.EncodeToString(raw))
		}
	}
	return found
}

func TestPrintedCommandsKeepTheirValuesInTheShell(t *testing.T) {
	folder := awkwardFolder(t)
	printed := run(t, nil, "ws", "--dir", folder, "init", "--workstation", "ws-a", "--base-domain", "lvh.me")
	if printed.code != 0 {
		t.Fatalf("init: %+v", printed)
	}
	// init prints one lvh-yard command and one docker command that start the same services.
	var lines []string
	for line := range strings.SplitSeq(printed.stdout, "\n") {
		if line = strings.TrimSpace(line); strings.HasSuffix(line, " compose start") || strings.HasSuffix(line, " up -d") {
			lines = append(lines, line)
		}
	}
	if len(lines) != 2 {
		t.Fatalf("want two start commands in: %s", printed.stdout)
	}
	// The printed commands name lvh-yard and docker, so the shell must find both on the PATH.
	if filepath.Base(executable) != executableName("lvh-yard") {
		t.Fatalf("the executable must have the name lvh-yard: %s", executable)
	}
	bin := t.TempDir()
	_, env, recorded := standIn(t, bin, "docker", "docker")
	env = append(env, pathWith(bin, filepath.Dir(executable)))
	want := []string{"compose", "--project-directory", folder, "up", "-d"}
	for name, shell := range shells(t) {
		for _, line := range lines {
			t.Run(name+" "+strings.Fields(line)[0], func(t *testing.T) {
				cmd := shell(line)
				cmd.Env = append(os.Environ(), env...)
				cmd.Dir = t.TempDir()
				if output, err := cmd.CombinedOutput(); err != nil {
					t.Fatalf("%s\n%v\n%s", line, err, output)
				}
				if docker := recorded(); !reflect.DeepEqual(docker.Args, want) {
					t.Fatalf("%s\ndocker got %q, want %q", line, docker.Args, want)
				}
			})
		}
	}
}
