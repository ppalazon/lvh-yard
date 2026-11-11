package command_test

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestProfilesEdit(t *testing.T) {
	for _, tc := range []struct {
		name, editor, input, wantOut, wantErr string
		transform                             func(string, int) string
		editorError                           bool
		refused                               bool
		wantEdits, wantSaves, wantCode        int
	}{
		{name: "changed with editor arguments", editor: "code --wait", transform: func(s string, _ int) string { return strings.ReplaceAll(s, "X-Team: a", "X-Team: b") }, wantOut: "Saved the Route Profile team.\n", wantEdits: 1, wantSaves: 1},
		{name: "unchanged defaults to vi", editor: "  ", transform: func(s string, _ int) string { return s }, wantOut: "No change to the Route Profile team.\n", wantEdits: 1},
		{name: "different name", input: "n\n", transform: func(s string, _ int) string { return strings.ReplaceAll(s, "team:", "crew:") }, wantErr: "The file must hold only the Route Profile team. It holds crew.\nEdit again? [Y/n] ", wantEdits: 1, wantCode: 1},
		{name: "second profile", input: "n\n", transform: func(s string, _ int) string { return s + "    gzip:\n      compress: {}\n" }, wantErr: "The file must hold only the Route Profile team. It holds team, gzip.\n", wantEdits: 1, wantCode: 1},
		{name: "retry keeps text", input: "\n", transform: func(s string, call int) string {
			if call == 1 {
				return strings.ReplaceAll(s, "team:", "crew:")
			}
			if !strings.Contains(s, "crew:") {
				t.Error("retry lost the edited text")
			}
			return strings.ReplaceAll(strings.ReplaceAll(s, "crew:", "team:"), "X-Team: a", "X-Team: b")
		}, wantOut: "Saved the Route Profile team.\n", wantErr: "The file must hold only the Route Profile team. It holds crew.\nEdit again? [Y/n] ", wantEdits: 2, wantSaves: 1},
		{name: "refused save", input: "n\n", transform: func(s string, _ int) string { return strings.ReplaceAll(s, "headers:", "headerz:") }, refused: true, wantErr: "team has the type headerz.\nteam has invalid options.\nEdit again? [Y/n] ", wantEdits: 1, wantSaves: 1, wantCode: 1},
		{name: "end of input stops retry", transform: func(s string, _ int) string { return "broken: [\n" }, wantErr: "The file is not valid YAML", wantEdits: 1, wantCode: 1},
		{name: "editor fails unchanged", transform: func(s string, _ int) string { return s }, editorError: true, wantErr: "Cannot edit the Route Profile with vi: editor failed", wantEdits: 1, wantCode: 1},
		{name: "editor fails changed", transform: func(s string, _ int) string { return s + "# changed\n" }, editorError: true, wantErr: "Cannot edit the Route Profile with vi: editor failed", wantEdits: 1, wantCode: 1},
	} {
		t.Run(tc.name, func(t *testing.T) {
			edits, saves := 0, 0
			var tempPath, written string
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.URL.Path != "/api/workstation/route-profiles/team" {
					t.Errorf("unexpected path: %s", r.URL)
				}
				if r.Method == "GET" {
					fmt.Fprint(w, `{"name":"team","definition":{"headers":{"customRequestHeaders":{"X-Team":"a"}}}}`)
					return
				}
				if r.Method != "PUT" {
					t.Errorf("unexpected method: %s", r.Method)
				}
				saves++
				if tc.refused {
					w.WriteHeader(http.StatusBadRequest)
					fmt.Fprint(w, `{"problems":[{"profile":"team","reason":"team has the type headerz."},{"profile":"team","reason":"team has invalid options."}]}`)
					return
				}
				var got any
				if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
					t.Error(err)
				}
				want := map[string]any{"headers": map[string]any{"customRequestHeaders": map[string]any{"X-Team": "b"}}}
				if !reflect.DeepEqual(got, want) {
					t.Errorf("unexpected profile: %#v", got)
				}
				fmt.Fprint(w, `{"name":"team","definition":{"headers":{"customRequestHeaders":{"X-Team":"b"}}}}`)
			}))
			defer server.Close()
			h := commandtest.New(t)
			h.Process.Env["EDITOR"] = tc.editor
			h.Process.Stdin = tc.input
			h.Process.Editor = func(editor, path string) error {
				edits++
				wantEditor := tc.editor
				if strings.TrimSpace(wantEditor) == "" {
					wantEditor = "vi"
				}
				if editor != wantEditor {
					t.Errorf("editor arguments changed: %q", editor)
				}
				if tempPath != "" && tempPath != path {
					t.Errorf("retry used another file: %s", path)
				}
				tempPath = path
				info, err := os.Stat(path)
				if err != nil {
					return err
				}
				if info.Mode().Perm() != 0600 {
					t.Errorf("private profile has permissions %o", info.Mode().Perm())
				}
				text, err := os.ReadFile(path)
				if err != nil {
					return err
				}
				written = tc.transform(string(text), edits)
				if err := os.WriteFile(path, []byte(written), 0600); err != nil {
					return err
				}
				if tc.editorError {
					return errors.New("editor failed")
				}
				return nil
			}
			result := h.Run("--dashboard", server.URL, "profiles", "edit", "team")
			if result.ExitCode != tc.wantCode || result.Stdout != tc.wantOut || !strings.HasPrefix(result.Stderr, tc.wantErr) || edits != tc.wantEdits || saves != tc.wantSaves {
				t.Fatalf("unexpected edit: %+v; edits %d, saves %d", result, edits, saves)
			}
			if tc.wantErr == "" && result.Stderr != "" {
				t.Errorf("unexpected stderr: %s", result.Stderr)
			}
			if tc.wantCode == 1 {
				if want := "Did not save the Route Profile team. Your text is in " + tempPath + ".\n"; !strings.HasSuffix(result.Stderr, want) {
					t.Errorf("missing note about the kept text: %s", result.Stderr)
				}
				kept, err := os.ReadFile(tempPath)
				if err != nil || string(kept) != written {
					t.Errorf("kept text is %q, %v; want %q", kept, err, written)
				}
				if info, err := os.Stat(tempPath); err == nil && info.Mode().Perm() != 0600 {
					t.Errorf("kept text has permissions %o", info.Mode().Perm())
				}
				os.Remove(tempPath)
			} else if _, err := os.Stat(tempPath); !errors.Is(err, os.ErrNotExist) {
				t.Errorf("temporary profile was not removed: %s: %v", tempPath, err)
			}
		})
	}
}

func TestProfilesCommandsRequireOneArgument(t *testing.T) {
	for _, command := range []string{"show", "set", "edit", "remove"} {
		for _, args := range [][]string{{"profiles", command}, {"profiles", command, "a", "b"}} {
			result := commandtest.New(t).Run(args...)
			if result.ExitCode != 1 || result.Stdout != "" || result.Stderr == "" {
				t.Fatalf("invalid arguments accepted: %v: %+v", args, result)
			}
		}
	}
}
