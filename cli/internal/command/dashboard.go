package command

import (
	"os"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
)

const defaultDashboard = "http://127.0.0.1:7780"

func (o *Options) DashboardAddress() string {
	return o.dashboardAddressIn(o.WorkstationFolder())
}

// dashboardAddressIn is the address of the dashboard of a Workstation Folder, unless --dashboard or
// YARD_DASHBOARD_URL names one.
func (o *Options) dashboardAddressIn(folder string) string {
	if o.dashboard != "" {
		return o.dashboard
	}
	if url := o.Process.Getenv("YARD_DASHBOARD_URL"); strings.TrimSpace(url) != "" {
		return url
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return defaultDashboard
	}
	defer func() { _ = root.Close() }()
	text, err := root.ReadFile(".env")
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
	return defaultWorkstationFolder(o.Process)
}
