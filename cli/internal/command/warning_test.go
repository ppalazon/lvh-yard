package command

import (
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
)

func TestWarningLine(t *testing.T) {
	for _, tc := range []struct {
		name    string
		warning client.ServiceWarning
		want    string
	}{
		{"topic", client.ServiceWarning{Severity: "risk", Topic: "Loopback", Message: "It is on loopback."}, "risk: [Loopback] It is on loopback."},
		{"older dashboard without a topic", client.ServiceWarning{Severity: "error", Message: "It does not exist."}, "error: It does not exist."},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := warningLine(tc.warning); got != tc.want {
				t.Fatalf("got %q, want %q", got, tc.want)
			}
		})
	}
}
