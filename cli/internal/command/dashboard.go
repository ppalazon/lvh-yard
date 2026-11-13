package command

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
)

const defaultDashboard = "http://127.0.0.1:7780"

func (o *Options) DashboardAddress() string {
	if o.dashboard != "" {
		return o.dashboard
	}
	if url := o.Process.Getenv("YARD_DASHBOARD_URL"); strings.TrimSpace(url) != "" {
		return url
	}
	text, err := os.ReadFile(filepath.Join(o.WorkstationFolder(), ".env"))
	if err != nil {
		return defaultDashboard
	}
	env := string(text)
	bind := envfile.Setting(env, "YARD_DASHBOARD_BIND_ADDRESS", "127.0.0.1")
	if bind == "0.0.0.0" {
		bind = "127.0.0.1"
	}
	port := envfile.Setting(env, "YARD_DASHBOARD_PORT", "7780")
	return "http://" + bind + ":" + port
}

func (o *Options) WorkstationFolder() string {
	if folder := o.Process.Getenv("YARD_CONFIG_DIR"); strings.TrimSpace(folder) != "" {
		return folder
	}
	if folder := o.Process.Getenv("XDG_CONFIG_HOME"); strings.TrimSpace(folder) != "" {
		return filepath.Join(folder, "lvh-yard")
	}
	home := o.Process.Getenv("HOME")
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	return filepath.Join(home, ".config", "lvh-yard")
}
