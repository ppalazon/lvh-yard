package command

import (
	"fmt"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
)

// warningLine is "level: [Topic] message". A dashboard that sends no topic gives "level: message".
func warningLine(warning client.ServiceWarning) string {
	if warning.Topic == "" {
		return fmt.Sprintf("%s: %s", warning.Severity, warning.Message)
	}
	return fmt.Sprintf("%s: [%s] %s", warning.Severity, warning.Topic, warning.Message)
}
