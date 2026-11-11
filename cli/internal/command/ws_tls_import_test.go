package command_test

import (
 "os"
 "path/filepath"
 "strings"
 "testing"

 "github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSTLSImport(t *testing.T) {
 yard := commandtest.New(t)
 source := t.TempDir()
 run := func(args ...string) { t.Helper(); if r := yard.Run(args...); r.ExitCode != 0 { t.Fatal(r) } }
 run("ws", "init", "--dir", source, "--workstation", "ws-a")
 run("ws", "tls", "ca", "create", "--dir", source)
 run("ws", "tls", "cert", "create", "--dir", source)
 run("ws", "tls", "cert", "create", "--dir", source, "--workstation", "ws-b")
 other := t.TempDir()
 run("ws", "init", "--dir", other, "--workstation", "ws-a")
 run("ws", "tls", "ca", "create", "--dir", other)
 for _, tc := range []struct{name, ca, cert, key, message string}{
  {"key", source+"/tls/ca.crt", source+"/tls/workstation.crt", source+"/certificates/ws-b/workstation.key", "key"},
  {"CA", other+"/ca/ca.crt", source+"/tls/workstation.crt", source+"/tls/workstation.key", "CA"},
  {"name", source+"/tls/ca.crt", source+"/certificates/ws-b/workstation.crt", source+"/certificates/ws-b/workstation.key", "server.ws-a.consul"},
 } { t.Run(tc.name, func(t *testing.T) {
  r := yard.Run("ws", "tls", "import", "--dir", other, "--ca", tc.ca, "--cert", tc.cert, "--key", tc.key)
  if r.ExitCode == 0 || !strings.Contains(r.Stderr, tc.message) { t.Fatal(r) }
  if _, err := os.Stat(filepath.Join(other,"tls","workstation.key")); !os.IsNotExist(err) { t.Fatal("refused import wrote TLS files") }
 }) }
 r := yard.Run("ws", "tls", "import", "--dir", other, "--ca", source+"/tls/ca.crt", "--cert", source+"/tls/workstation.crt", "--key", source+"/tls/workstation.key")
 if r.ExitCode != 0 || !strings.Contains(r.Stdout,"cannot federate") { t.Fatal(r) }
 for _, name := range []string{"ca.crt", "workstation.crt", "workstation.key"} {
  want, _ := os.ReadFile(filepath.Join(source,"tls",name)); got, err := os.ReadFile(filepath.Join(other,"tls",name))
  if err != nil || string(got) != string(want) { t.Fatalf("imported %s: %v", name, err) }
 }
 assertPrivateWorkstation(t, other)
 configPath := filepath.Join(other,"consul.d","10-tls.hcl")
 config, err := os.ReadFile(configPath); if err != nil { t.Fatal(err) }
 for _, setting := range []string{"verify_incoming = true", "verify_outgoing = true", "verify_server_hostname = true", "http", "verify_incoming = false"} { if !strings.Contains(string(config), setting) { t.Fatalf("missing %s",setting) } }
 if err := os.WriteFile(configPath, []byte("broken"),0600); err != nil { t.Fatal(err) }
 run("ws","upgrade","--dir",other)
 restored, err := os.ReadFile(configPath); if err != nil || string(restored) != string(config) { t.Fatal("upgrade did not regenerate TLS") }
}
