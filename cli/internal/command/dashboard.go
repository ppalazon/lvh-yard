package command

import (
	"os"
	"path/filepath"
	"strings"
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
	bind := envSetting(env, "YARD_DASHBOARD_BIND_ADDRESS", "127.0.0.1")
	if bind == "0.0.0.0" {
		bind = "127.0.0.1"
	}
	port := envSetting(env, "YARD_DASHBOARD_PORT", "7780")
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

func envSetting(text, key, fallback string) string {
	for line := range strings.SplitSeq(text, "\n") {
		line = strings.TrimSuffix(line, "\r")
		if value, ok := strings.CutPrefix(line, key+"="); ok {
			if strings.TrimSpace(value) != "" {
				return value
			}
			return fallback
		}
	}
	return fallback
}
