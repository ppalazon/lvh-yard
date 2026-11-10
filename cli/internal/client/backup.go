package client

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"strings"
)

func (c *Client) Backup() (string, error) {
	data, err := c.exchange(http.MethodGet, "api/workstation/backup", "", "application/yaml, application/problem+json", nil)
	return string(data), err
}

func (c *Client) ImportBackup(backup, mode string, dryRun bool) (ImportResult, error) {
	path := "api/workstation/backup?mode=" + url.QueryEscape(mode)
	if dryRun {
		path += "&dry-run=true"
	}
	data, err := c.exchange(http.MethodPost, path, "application/yaml", "application/json", strings.NewReader(backup))
	if err != nil {
		if failure := importFailure(data); failure != "" {
			return ImportResult{}, errors.New(failure)
		}
		return ImportResult{}, err
	}
	var result ImportResult
	if err := json.Unmarshal(data, &result); err != nil {
		return ImportResult{}, fmt.Errorf("%s is not the LVH Yard dashboard: its answer is not the API's JSON.", c.address)
	}
	return result, nil
}

func importFailure(data []byte) string {
	var failure struct {
		Detail       string      `json:"detail"`
		Applied      []PlanEntry `json:"applied"`
		SafetyBackup string      `json:"safetyBackup"`
	}
	if json.Unmarshal(data, &failure) != nil || failure.Applied == nil || failure.SafetyBackup == "" {
		return ""
	}
	var text strings.Builder
	text.WriteString(failure.Detail)
	if len(failure.Applied) == 0 {
		text.WriteString("\nIt applied nothing.")
	} else {
		text.WriteString("\nIt applied:")
		for _, entry := range failure.Applied {
			text.WriteByte('\n')
			text.WriteString(entry.Line())
		}
	}
	fmt.Fprintf(&text, "\nSafety Backup: %s, in the Backups folder. To go back, import it.", failure.SafetyBackup)
	return text.String()
}

func (entry PlanEntry) Line() string {
	var what string
	switch entry.Kind {
	case "route-profile":
		what = "Route Profile " + entry.Name
	case "service":
		what = "Service " + entry.Name
	case "publication":
		what = "Publication of " + entry.Name
	case "instance-priority":
		what = "Instance Priority of " + entry.Name
	case "static-instance":
		what = "Static Instance " + entry.Name
	case "container-binding":
		what = "Container Binding " + entry.Name
	case "alias":
		what = "Alias to " + entry.Name
	default:
		what = entry.Kind + " " + entry.Name
	}
	line := entry.Action + " " + what
	if entry.Service != "" {
		line += " of " + entry.Service
	}
	if entry.Reason != "" {
		line += ": " + entry.Reason
	}
	return line
}
