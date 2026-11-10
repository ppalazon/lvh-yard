package command_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestContainersGroupsEveryContainerAndMarksUnfollowed(t *testing.T) {
	body := `[{"name":"beta-web-1","composeProject":"beta","composeService":"web","service":"web","running":true,"followedBy":[],"ports":[{"containerPort":8080}]},{"name":"alpha-web-2","composeProject":"alpha","composeService":"web","service":"web","running":false,"followedBy":[],"ports":[{"containerPort":9000},{"containerPort":80}]},{"name":"alpha-worker-1","composeProject":"alpha","composeService":"worker","service":"worker","running":true,"followedBy":["jobs"],"ports":[]},{"name":"alpha-web-1","composeProject":"alpha","composeService":"web","service":"web","running":true,"followedBy":[],"ports":[{"containerPort":443},{"containerPort":80}]},{"name":"shop-Front_End-1","composeProject":"shop","composeService":"Front_End","service":"front-end","running":true,"followedBy":["shop"],"ports":[{"containerPort":80}]}]`
	run := runContainers(t, body)
	want := "Compose project alpha\n  web: 2 containers; 80, 443, 9000; 1 running, 1 stopped; Unfollowed: alpha-web-1\n  worker: 1 container; no ports\nCompose project beta\n  web: 1 container; 8080; Unfollowed: beta-web-1\nCompose project shop\n  Front_End: 1 container; 80; registers as front-end\n"
	if run.ExitCode != 0 || run.Stderr != "" || run.Stdout != want {
		t.Fatalf("got %+v; want stdout %q", run, want)
	}
}

func TestContainerDetailsRetainMappingsStateHealthAndUnfollowedMarkers(t *testing.T) {
	body := `[{"name":"metrics-2","service":"metrics","running":true,"followedBy":[],"health":"starting","ports":[{"containerPort":9090}]},{"name":"worker-1","service":"worker","running":false,"followedBy":[],"ports":[]},{"name":"metrics-1","service":"metrics","running":true,"followedBy":["metrics"],"health":"healthy","ports":[{"containerPort":8080,"hostPort":37137,"hostIp":"::"},{"containerPort":8080,"hostPort":37138,"hostIp":"127.0.0.1"},{"containerPort":8080,"hostPort":37137,"hostIp":"0.0.0.0"},{"containerPort":8080,"hostPort":37137,"hostIp":"0.0.0.0"}]},{"name":"database-1","service":"database","running":true,"followedBy":[],"ports":[{"containerPort":5432}]},{"name":"unknown-1","service":"unknown","running":true,"followedBy":["web"],"ports":[{"containerPort":8080,"hostPort":37137}]}]`
	run := runContainers(t, body, "--details")
	want := "Other containers\n  database: 1 container; 5432; Unfollowed: database-1\n    database-1: 5432 not published; running; Unfollowed\n  metrics: 1 container; 8080\n    metrics-1: 0.0.0.0:37137->8080, 127.0.0.1:37138->8080, [::]:37137->8080; running; healthy\n  metrics: 1 container; 9090; Unfollowed: metrics-2\n    metrics-2: 9090 not published; running; Unfollowed; starting\n  unknown: 1 container; 8080\n    unknown-1: 37137->8080 (bind address unknown); running\n  worker: 1 container; no ports; 0 running, 1 stopped\n    worker-1: no ports; stopped\n"
	if run.ExitCode != 0 || run.Stderr != "" || run.Stdout != want {
		t.Fatalf("got %+v; want stdout %q", run, want)
	}
}

func TestContainerDetailsSortComposeReplicas(t *testing.T) {
	run := runContainers(t, `[{"name":"shop-web-2","composeProject":"shop","composeService":"web","service":"web","running":true,"followedBy":[],"ports":[{"containerPort":80,"hostPort":19082}]},{"name":"shop-web-1","composeProject":"shop","composeService":"web","service":"web","running":true,"followedBy":[],"ports":[{"containerPort":80,"hostPort":19081}]}]`, "--details")
	if run.ExitCode != 0 || !strings.Contains(run.Stdout, "web: 2 containers; 80; Unfollowed: shop-web-1, shop-web-2") || strings.Index(run.Stdout, "    shop-web-1:") > strings.Index(run.Stdout, "    shop-web-2:") {
		t.Fatalf("unexpected replicas: %+v", run)
	}
}

func TestContainersEmptyAndUsage(t *testing.T) {
	run := runContainers(t, `[]`)
	if run.ExitCode != 0 || run.Stdout != "No containers on this Workstation.\n" || run.Stderr != "" {
		t.Fatalf("unexpected empty list: %+v", run)
	}
	run = commandtest.New(t).Run("containers", "extra")
	if run.ExitCode != 1 || run.Stderr == "" {
		t.Fatalf("unexpected arguments accepted: %+v", run)
	}
}

func runContainers(t *testing.T, body string, flags ...string) commandtest.Result {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.URL.Path != "/api/workstation/containers" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		w.Write([]byte(body))
	}))
	defer server.Close()
	return commandtest.New(t).Run(append([]string{"--dashboard", server.URL, "containers"}, flags...)...)
}
