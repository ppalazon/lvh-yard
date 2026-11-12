package command_test

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestProfilesList(t *testing.T) {
	for _, tc := range []struct{ response, want string }{
		{`[{"name":"behind-https","type":"headers","services":["api","web"]},{"name":"team-login","type":"basicAuth","services":[]},{"name":"old","services":[]}]`, "behind-https: headers, used by api, web\nteam-login: basicAuth, not used\nold: not used\n"},
		{`[]`, "No Route Profiles.\n"},
	} {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != "GET" || r.URL.Path != "/api/workstation/route-profiles" {
				t.Errorf("unexpected request: %s %s", r.Method, r.URL)
			}
			fmt.Fprint(w, tc.response)
		}))
		result := commandtest.New(t).Run("--dashboard", server.URL, "profiles")
		server.Close()
		if result.ExitCode != 0 || result.Stdout != tc.want || result.Stderr != "" {
			t.Fatalf("unexpected list result: %+v", result)
		}
	}
}

func TestProfilesShowSetRoundTrip(t *testing.T) {
	const definition = `{"headers":{"customRequestHeaders":{"X-Proto":"https","X-Number":"100","X-Yes":"true","X-On":"on","X-Empty":"","X-Colon":"a: b","X-Hash":"#1","X-Null":"null"},"sslRedirect":true,"accessControlMaxAge":100,"maxBody":2000000,"big":9007199254740993,"hostsProxyHeaders":[],"nested":{"values":[null,false,1.5,"100",{}]}}}`
	var received map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == "GET" && r.URL.Path == "/api/workstation/route-profiles/behind-https":
			fmt.Fprint(w, `{"name":"behind-https","definition":`+definition+`}`)
		case r.Method == "PUT" && r.URL.Path == "/api/workstation/route-profiles":
			// UseNumber keeps integers above 2^53 exact in this comparison.
			decoder := json.NewDecoder(r.Body)
			decoder.UseNumber()
			if err := decoder.Decode(&received); err != nil {
				t.Error(err)
			}
			fmt.Fprint(w, `[]`)
		default:
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
	}))
	defer server.Close()
	h := commandtest.New(t)
	shown := h.Run("--dashboard", server.URL, "profiles", "show", "behind-https")
	if shown.ExitCode != 0 || shown.Stderr != "" || !strings.HasPrefix(shown.Stdout, "http:\n  middlewares:\n    behind-https:\n") {
		t.Fatalf("unexpected show: %+v", shown)
	}
	for _, line := range []string{"maxBody: 2000000\n", "big: 9007199254740993\n"} {
		if !strings.Contains(shown.Stdout, line) {
			t.Errorf("missing %q in:\n%s", line, shown.Stdout)
		}
	}
	h.Process.Stdin = shown.Stdout
	set := h.Run("--dashboard", server.URL, "profiles", "set", "-")
	var want any
	decoder := json.NewDecoder(strings.NewReader(definition))
	decoder.UseNumber()
	if err := decoder.Decode(&want); err != nil {
		t.Fatal(err)
	}
	if set.ExitCode != 0 || set.Stdout != "Saved the Route Profile behind-https.\n" || set.Stderr != "" || !reflect.DeepEqual(received, map[string]any{"behind-https": want}) {
		t.Fatalf("round trip changed profile: %+v; received %#v", set, received)
	}
}

func TestProfilesSetFileAsOneBatch(t *testing.T) {
	calls := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.Method != "PUT" || r.URL.Path != "/api/workstation/route-profiles" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		var got map[string]any
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Error(err)
		}
		want := map[string]any{"office-only": map[string]any{"ipAllowList": map[string]any{"sourceRange": []any{"192.168.1.0/24"}}}, "protected": map[string]any{"chain": map[string]any{"middlewares": []any{"office-only"}}}}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("unexpected set: %#v", got)
		}
		fmt.Fprint(w, `[]`)
	}))
	defer server.Close()
	file := filepath.Join(t.TempDir(), "profiles.yaml")
	if err := os.WriteFile(file, []byte("http:\n  middlewares:\n    office-only:\n      ipAllowList:\n        sourceRange: [192.168.1.0/24]\n    protected:\n      chain:\n        middlewares: [office-only]\n"), 0600); err != nil {
		t.Fatal(err)
	}
	result := commandtest.New(t).Run("--dashboard", server.URL, "profiles", "set", file)
	if result.ExitCode != 0 || result.Stdout != "Saved the Route Profiles office-only, protected.\n" || result.Stderr != "" || calls != 1 {
		t.Fatalf("unexpected set: %+v; calls %d", result, calls)
	}
}

func TestClientCertificateProfileFileRoundTrip(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == "GET" && r.URL.Path == "/api/workstation/route-profiles/certificate" {
			fmt.Fprint(w, `{"name":"certificate","type":"clientCertificate","services":[],"definition":{"clientCertificate":{}}}`)
			return
		}
		if r.Method != "PUT" || r.URL.Path != "/api/workstation/route-profiles" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		var got map[string]any
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Error(err)
		}
		want := map[string]any{"certificate": map[string]any{"clientCertificate": map[string]any{}}}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("unexpected certificate profile: %#v", got)
		}
		fmt.Fprint(w, `[]`)
	}))
	defer server.Close()
	h := commandtest.New(t)
	shown := h.Run("--dashboard", server.URL, "profiles", "show", "certificate")
	if shown.ExitCode != 0 || !strings.Contains(shown.Stdout, "clientCertificate: {}") {
		t.Fatalf("unexpected show: %+v", shown)
	}
	file := filepath.Join(t.TempDir(), "profiles.yaml")
	if err := os.WriteFile(file, []byte(shown.Stdout), 0600); err != nil {
		t.Fatal(err)
	}
	saved := h.Run("--dashboard", server.URL, "profiles", "set", file)
	if saved.ExitCode != 0 || saved.Stdout != "Saved the Route Profile certificate.\n" {
		t.Fatalf("unexpected save: %+v", saved)
	}
}

func TestProfilesSetRejectsInvalidFilesBeforeRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { t.Errorf("unexpected request: %s %s", r.Method, r.URL) }))
	defer server.Close()
	for _, tc := range []struct{ name, text, reason string }{
		{"wrong root", "gzip:\n  compress: {}\n", "http.middlewares"},
		{"empty", "", "http.middlewares"},
		{"no profiles", "http:\n  middlewares: {}\n", "names no Route Profile"},
		{"wrong type", "http:\n  middlewares: []\n", "http.middlewares"},
		{"extra root", "http:\n  middlewares:\n    gzip: {compress: {}}\ntcp: {}\n", "http.middlewares"},
		{"extra http", "http:\n  middlewares:\n    gzip: {compress: {}}\n  routers: {}\n", "http.middlewares"},
		{"bad yaml", "http:\n  middlewares: [unclosed\n", "The file is not valid YAML"},
		{"duplicate name", "http:\n  middlewares:\n    gzip: {compress: {}}\n    gzip: {compress: {}}\n", "The file is not valid YAML"},
		{"second document", "http:\n  middlewares: {gzip: {compress: {}}}\n---\nhttp: {}\n", "http.middlewares"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			h := commandtest.New(t)
			h.Process.Stdin = tc.text
			result := h.Run("--dashboard", server.URL, "profiles", "set", "-")
			if result.ExitCode != 1 || result.Stdout != "" || !strings.Contains(result.Stderr, tc.reason) {
				t.Fatalf("unexpected invalid set: %+v", result)
			}
		})
	}
	missing := commandtest.New(t).Run("profiles", "set", filepath.Join(t.TempDir(), "missing.yaml"))
	if missing.ExitCode != 1 || !strings.Contains(missing.Stderr, "missing.yaml") {
		t.Fatalf("unexpected missing file: %+v", missing)
	}
}

func TestProfilesSetPrintsEachProblem(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprint(w, `{"detail":"Combined reasons.","problems":[{"profile":"from-file","reason":"from-file reads its users from usersFile."},{"profile":"typo","reason":"typo has the type basicAut."}]}`)
	}))
	defer server.Close()
	h := commandtest.New(t)
	h.Process.Stdin = "http:\n  middlewares:\n    from-file: {basicAuth: {usersFile: /etc/users}}\n    typo: {basicAut: {}}\n"
	result := h.Run("--dashboard", server.URL, "profiles", "set", "-")
	if result.ExitCode != 1 || result.Stdout != "" || result.Stderr != "from-file reads its users from usersFile.\ntypo has the type basicAut.\n" {
		t.Fatalf("unexpected refusal: %+v", result)
	}
}

func TestProfilesShowMissingAndRemove(t *testing.T) {
	for _, tc := range []struct {
		command, name     string
		status            int
		body, out, stderr string
	}{
		{"show", "nope", 404, `{"detail":"This Workstation has no Route Profile nope."}`, "", "This Workstation has no Route Profile nope.\n"},
		{"remove", "gzip", 204, "", "Removed the Route Profile gzip.\n", ""},
		{"remove", "login", 409, `{"detail":"login is in use. Services: web. Chains: protected.","services":["web"],"chains":["protected"]}`, "", "login is in use. Services: web. Chains: protected.\n"},
	} {
		t.Run(tc.command+" "+tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				method := "GET"
				if tc.command == "remove" {
					method = "DELETE"
				}
				if r.Method != method || r.URL.Path != "/api/workstation/route-profiles/"+tc.name {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				w.WriteHeader(tc.status)
				fmt.Fprint(w, tc.body)
			}))
			defer server.Close()
			result := commandtest.New(t).Run("--dashboard", server.URL, "profiles", tc.command, tc.name)
			wantCode := 0
			if tc.status >= 400 {
				wantCode = 1
			}
			if result.ExitCode != wantCode || result.Stdout != tc.out || result.Stderr != tc.stderr {
				t.Fatalf("unexpected result: %+v", result)
			}
		})
	}
}
