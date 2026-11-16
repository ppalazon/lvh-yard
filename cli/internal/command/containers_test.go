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
	run := runContainers(t, body, "--state", "all", "--followed", "all")
	want := "Compose project alpha\n  web: 2 containers; 80, 443, 9000; 1 running, 1 stopped; Unfollowed: alpha-web-1\n  worker: 1 container; no ports\nCompose project beta\n  web: 1 container; 8080; Unfollowed: beta-web-1\nCompose project shop\n  Front_End: 1 container; 80; registers as front-end\n"
	if run.ExitCode != 0 || run.Stderr != "" || run.Stdout != want {
		t.Fatalf("got %+v; want stdout %q", run, want)
	}
}

func TestContainerDetailsRetainMappingsStateHealthAndUnfollowedMarkers(t *testing.T) {
	body := `[{"name":"metrics-2","service":"metrics","running":true,"followedBy":[],"health":"starting","ports":[{"containerPort":9090}]},{"name":"worker-1","service":"worker","running":false,"followedBy":[],"ports":[]},{"name":"metrics-1","service":"metrics","running":true,"followedBy":["metrics"],"health":"healthy","ports":[{"containerPort":8080,"hostPort":37137,"hostIp":"::"},{"containerPort":8080,"hostPort":37138,"hostIp":"127.0.0.1"},{"containerPort":8080,"hostPort":37137,"hostIp":"0.0.0.0"},{"containerPort":8080,"hostPort":37137,"hostIp":"0.0.0.0"}]},{"name":"database-1","service":"database","running":true,"followedBy":[],"ports":[{"containerPort":5432}]},{"name":"unknown-1","service":"unknown","running":true,"followedBy":["web"],"ports":[{"containerPort":8080,"hostPort":37137}]}]`
	run := runContainers(t, body, "--state", "all", "--followed", "all", "--details")
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

const filterBody = `[{"name":"web-1","composeProject":"shop","composeService":"web","service":"web","running":true,"followedBy":["shop"],"ports":[]},{"name":"web-2","composeProject":"shop","composeService":"web","service":"web","running":true,"followedBy":[],"ports":[]},{"name":"db-1","composeProject":"shop","composeService":"db","service":"db","running":true,"followedBy":[],"ports":[]},{"name":"old-1","composeProject":"shop","composeService":"old","service":"old","running":false,"followedBy":[],"ports":[]},{"name":"cache","service":"cache","running":true,"followedBy":["cache"],"ports":[]},{"name":"tool","service":"tool","running":true,"followedBy":[],"ports":[]},{"name":"idle","service":"idle","running":false,"followedBy":["idle"],"ports":[]}]`

func TestContainersShowRunningUnfollowedByDefault(t *testing.T) {
	run := runContainers(t, filterBody)
	want := "Compose project shop\n  db: 1 container; no ports; Unfollowed: db-1\nOther containers\n  tool: 1 container; no ports; Unfollowed: tool\n"
	if run.ExitCode != 0 || run.Stderr != "" || run.Stdout != want {
		t.Fatalf("got %+v; want stdout %q", run, want)
	}
}

func TestContainersFilterByStateAndFollowed(t *testing.T) {
	tests := []struct {
		flags []string
		want  []string
		not   []string
	}{
		{[]string{"--state", "stopped"}, []string{"  old: 1 container; no ports; 0 running, 1 stopped"}, []string{"db:", "idle:", "web:", "tool:", "cache:"}},
		{[]string{"--followed", "yes"}, []string{"  web: 2 containers", "  cache: 1 container"}, []string{"db:", "old:", "idle:", "tool:"}},
		{[]string{"--state", "all", "--followed", "yes"}, []string{"  web: 2 containers", "  cache: 1 container", "  idle: 1 container"}, []string{"db:", "old:", "tool:"}},
		{[]string{"--state", "all", "--followed", "all"}, []string{"  web: 2 containers", "  db:", "  old:", "  cache:", "  tool:", "  idle:"}, nil},
	}
	for _, test := range tests {
		run := runContainers(t, filterBody, test.flags...)
		if run.ExitCode != 0 || run.Stderr != "" {
			t.Fatalf("%v: %+v", test.flags, run)
		}
		for _, want := range test.want {
			if !strings.Contains(run.Stdout, want) {
				t.Errorf("%v: missing %q in %q", test.flags, want, run.Stdout)
			}
		}
		for _, not := range test.not {
			if strings.Contains(run.Stdout, not) {
				t.Errorf("%v: unexpected %q in %q", test.flags, not, run.Stdout)
			}
		}
	}
}

func TestContainersSayWhenNothingMatchesAndRefuseUnknownValues(t *testing.T) {
	run := runContainers(t, `[{"name":"tool","service":"tool","running":false,"followedBy":[],"ports":[]}]`)
	if run.ExitCode != 0 || run.Stdout != "No containers match. Use --state all and --followed all to list every container.\n" {
		t.Fatalf("unexpected output: %+v", run)
	}
	for _, flags := range [][]string{{"--state", "up"}, {"--followed", "maybe"}} {
		run = runContainers(t, filterBody, flags...)
		if run.ExitCode != 1 || !strings.Contains(run.Stderr, "unknown") {
			t.Fatalf("%v accepted: %+v", flags, run)
		}
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
