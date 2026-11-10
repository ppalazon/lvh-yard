package clienttest

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync/atomic"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"go.yaml.in/yaml/v3"
)

// AssertOperations checks requests from real client calls, including escaped path segments.
// Responses are empty JSON objects; operation calls may ignore decoding errors.
func AssertOperations(t testing.TB, operations ...func(*client.Client)) {
	t.Helper()
	_, file, _, _ := runtime.Caller(0)
	data, err := os.ReadFile(filepath.Join(filepath.Dir(file), "../../../../dashboard/openapi.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	var document struct {
		Paths map[string]map[string]any `yaml:"paths"`
	}
	if err := yaml.Unmarshal(data, &document); err != nil {
		t.Fatal(err)
	}
	var requests atomic.Int64
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		if !HasOperation(document.Paths, r.Method, r.URL.EscapedPath()) {
			t.Errorf("OpenAPI lacks %s %s", r.Method, r.URL.EscapedPath())
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{}`))
	}))
	defer server.Close()
	for _, operation := range operations {
		before := requests.Load()
		operation(client.New(server.URL))
		if requests.Load() == before {
			t.Error("operation did not send a request")
		}
	}
}

// HasOperation matches each template parameter to exactly one escaped segment.
func HasOperation(paths map[string]map[string]any, method, path string) bool {
	actual := strings.Split(path, "/")
	for template, methods := range paths {
		if _, ok := methods[strings.ToLower(method)]; !ok {
			continue
		}
		parts := strings.Split(template, "/")
		if len(parts) != len(actual) {
			continue
		}
		match := true
		for i, part := range parts {
			if strings.HasPrefix(part, "{") && strings.HasSuffix(part, "}") {
				if actual[i] == "" {
					match = false
					break
				}
			} else if part != actual[i] {
				match = false
				break
			}
		}
		if match {
			return true
		}
	}
	return false
}
