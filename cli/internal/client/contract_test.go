package client_test

import (
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestStatusMatchesOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t, func(c *client.Client) { _, _ = c.Status() })
}

func TestContractRejectsUnknownMethodAndPath(t *testing.T) {
	paths := map[string]map[string]any{"/api/services/{name}": {"get": nil}}
	for _, tc := range []struct {
		method, path string
		want         bool
	}{
		{"GET", "/api/services/a%2Fb", true},
		{"POST", "/api/services/a%2Fb", false},
		{"GET", "/api/missing/a", false},
		{"GET", "/api/services/a/b", false},
		{"GET", "/api/services/", false},
	} {
		if got := clienttest.HasOperation(paths, tc.method, tc.path); got != tc.want {
			t.Errorf("%s %s: got %v", tc.method, tc.path, got)
		}
	}
}
