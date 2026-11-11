package templates_test

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

func TestCommittedTemplatesMatchRepositoryBytes(t *testing.T) {
	for _, name := range []string{"compose.yaml", ".env.example"} {
		t.Run(name, func(t *testing.T) {
			root, err := os.ReadFile(filepath.Join("..", "..", "..", "config", name))
			if err != nil {
				t.Fatal(err)
			}
			copy, err := os.ReadFile(name)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(root, copy) {
				t.Fatalf("%s differs from config/; run mise run cli:templates", name)
			}
		})
	}
}
