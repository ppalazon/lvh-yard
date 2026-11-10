package command_test

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestCatalogDetailsShowsSourcePriorityOrder(t *testing.T) {
	for _, on := range []bool{true, false} {
		t.Run(fmt.Sprint(on), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				fmt.Fprintf(w, `{"home":{"name":"ws-a","services":[{"name":"web","state":"healthy","instancePriority":%t,"sources":[{"id":"fallback","kind":"static","instances":["fallback"]},{"id":"primary","kind":"static","instances":["primary"]}],"instances":[{"id":"primary","host":"127.0.0.1","port":8080,"health":"passing"},{"id":"fallback","host":"127.0.0.1","port":8081,"health":"passing"}]}]},"remotes":[]}`, on)
			}))
			defer server.Close()
			result := commandtest.New(t).Run("--dashboard", server.URL, "catalog", "--details")
			fallback := strings.Index(result.Stdout, "fallback")
			primary := strings.Index(result.Stdout, "primary")
			if result.ExitCode != 0 || fallback < 0 || primary < 0 || fallback > primary {
				t.Fatalf("catalog did not show the source order: %+v", result)
			}
		})
	}
}
