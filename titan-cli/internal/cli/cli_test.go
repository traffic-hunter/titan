package cli

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"github.com/spf13/cobra"
	"github.com/traffic-hunter/titan/titan-cli/internal/perf"
)

func TestRunRejectsUnknownCommand(t *testing.T) {
	var stderr bytes.Buffer

	code := Run([]string{"unknown"}, &bytes.Buffer{}, &stderr, "test")

	if code != 2 {
		t.Fatalf("expected exit code 2, got %d", code)
	}
	if !strings.Contains(stderr.String(), "unknown command") {
		t.Fatalf("expected unknown command message, got %q", stderr.String())
	}
}

func TestRunPrintsVersion(t *testing.T) {
	var stdout bytes.Buffer

	code := Run([]string{"version"}, &stdout, &bytes.Buffer{}, "0.7.0")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if strings.TrimSpace(stdout.String()) != "0.7.0" {
		t.Fatalf("expected version output, got %q", stdout.String())
	}
}

func TestRunPrintsHelp(t *testing.T) {
	var stdout bytes.Buffer

	code := Run([]string{"--help"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if !strings.Contains(stdout.String(), "Titan command-line tools") {
		t.Fatalf("expected root help, got %q", stdout.String())
	}
}

func TestRunPrintsPerfHelp(t *testing.T) {
	var stdout bytes.Buffer

	code := Run([]string{"perf-test", "--help"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	for _, expected := range []string{"end-to-end Titan performance test", "--messages", "--producers", "--payload-bytes"} {
		if !strings.Contains(stdout.String(), expected) {
			t.Fatalf("expected %q in output:\n%s", expected, stdout.String())
		}
	}
}

func TestRunRejectsInvalidPerfPayloadBeforeConnecting(t *testing.T) {
	var stderr bytes.Buffer

	code := Run([]string{"perf-test", "--payload-bytes", "8"}, &bytes.Buffer{}, &stderr, "test")

	if code != 1 {
		t.Fatalf("expected exit code 1, got %d", code)
	}
	if !strings.Contains(stderr.String(), "at least 24") {
		t.Fatalf("expected payload validation message, got %q", stderr.String())
	}
}

func TestPerfSettingsArePassedToPerfCommand(t *testing.T) {
	arguments := perfSettingsArguments(perfSettings{
		host:              "broker.internal",
		port:              "61613",
		transport:         perf.TransportWebSocket,
		sendMode:          perf.SendModeWrite,
		pathLabel:         perf.PathDirect,
		group:             "market",
		destination:       "/queue/orders",
		warmupMessages:    "250",
		messages:          "5000",
		producers:         "8",
		payloadBytes:      "2048",
		connectTimeout:    "3s",
		completionTimeout: "45s",
	})

	expected := []string{
		"perf-test",
		"--host", "broker.internal",
		"--port", "61613",
		"--transport", "websocket",
		"--send-mode", "write",
		"--path-label", "direct",
		"--group", "market",
		"--destination", "/queue/orders",
		"--warmup-messages", "250",
		"--messages", "5000",
		"--producers", "8",
		"--payload-bytes", "2048",
		"--connect-timeout", "3s",
		"--completion-timeout", "45s",
	}
	if !reflect.DeepEqual(arguments, expected) {
		t.Fatalf("unexpected performance settings: %v", arguments)
	}
}

func TestRunRejectsUnsupportedViewBeforeFetch(t *testing.T) {
	var stderr bytes.Buffer

	code := Run([]string{"--view", "bad", "--once"}, &bytes.Buffer{}, &stderr, "test")

	if code != 2 {
		t.Fatalf("expected exit code 2, got %d", code)
	}
	if !strings.Contains(stderr.String(), "unsupported view") {
		t.Fatalf("expected unsupported view message, got %q", stderr.String())
	}
}

func TestRunRendersOverviewOnce(t *testing.T) {
	server := snapshotServer(t)
	defer server.Close()
	var stdout bytes.Buffer

	code := Run([]string{"--addr", server.URL, "--once", "--no-clear", "--no-color"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	for _, expected := range []string{"Titan Monitor", "version     test", "Queues"} {
		if !strings.Contains(stdout.String(), expected) {
			t.Fatalf("expected %q in output:\n%s", expected, stdout.String())
		}
	}
}

func TestRunRendersQueueViewOnce(t *testing.T) {
	server := snapshotServer(t)
	defer server.Close()
	var stdout bytes.Buffer

	code := Run([]string{"--addr", server.URL, "--view", "queues", "--once", "--no-clear", "--no-color"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if !strings.Contains(stdout.String(), "/queue/orders") {
		t.Fatalf("expected queue output, got %q", stdout.String())
	}
}

func TestQueueListRendersQueues(t *testing.T) {
	server := queueServer(t, http.StatusOK)
	defer server.Close()
	var stdout bytes.Buffer

	code := Run([]string{"--addr", server.URL, "--no-color", "queue", "list"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if !strings.Contains(stdout.String(), "/queue/orders") {
		t.Fatalf("expected queue output, got %q", stdout.String())
	}
}

func TestQueueCreateUsesTokenFromEnvironment(t *testing.T) {
	t.Setenv("TITAN_MONITOR_TOKEN", "env-secret")
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer env-secret" {
			t.Fatalf("missing env bearer token")
		}
		if r.Method != http.MethodPost {
			t.Fatalf("expected POST, got %s", r.Method)
		}
		_, _ = w.Write([]byte(`{"group":"default","destination":"/queue/orders","size":0,"pendingBytes":0,"maxPendingBytes":30,"paused":false}`))
	}))
	defer server.Close()
	var stdout bytes.Buffer

	code := Run([]string{"--addr", server.URL, "queue", "create", "/queue/orders", "--max-pending-bytes", "30"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if !strings.Contains(stdout.String(), "created default:/queue/orders") {
		t.Fatalf("expected create output, got %q", stdout.String())
	}
}

func TestQueueDeleteConflictSuggestsForce(t *testing.T) {
	server := queueServer(t, http.StatusConflict)
	defer server.Close()
	var stderr bytes.Buffer

	code := Run([]string{"--addr", server.URL, "queue", "delete", "/queue/orders"}, &bytes.Buffer{}, &stderr, "test")

	if code != 1 {
		t.Fatalf("expected exit code 1, got %d", code)
	}
	if !strings.Contains(stderr.String(), "--force") {
		t.Fatalf("expected force suggestion, got %q", stderr.String())
	}
}

func snapshotServer(t *testing.T) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/titan/monitor/snapshot" {
			t.Fatalf("unexpected path %q", r.URL.Path)
		}
		_, _ = w.Write([]byte(`{
			"server":{"version":"test","uptimeMillis":1000},
			"jvm":{
				"cpu":{"systemCpuLoad":0.1,"processCpuLoad":0.2,"availableProcessors":8},
				"heap":{"used":512,"max":1024},
				"thread":{"threadCount":4,"peakThreadCount":8,"totalStartedThreadCount":16}
			},
			"queues":[{"group":"default","destination":"/queue/orders","size":5,"pendingBytes":20,"maxPendingBytes":40,"paused":false}]
		}`))
	}))
}

func queueServer(t *testing.T, status int) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/titan/monitor/queues" {
			t.Fatalf("unexpected path %q", r.URL.Path)
		}
		w.WriteHeader(status)
		if status == http.StatusOK {
			_, _ = w.Write([]byte(`[{"group":"default","destination":"/queue/orders","size":5,"pendingBytes":20,"maxPendingBytes":40,"paused":false}]`))
		}
	}))
}

func TestQueueActionCommandsSendActionAndUseEnvironmentToken(t *testing.T) {
	for _, action := range []string{"pause", "resume", "purge"} {
		t.Run(action, func(t *testing.T) {
			t.Setenv("TITAN_MONITOR_TOKEN", "env-secret")
			var requests int
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				requests++
				if r.Header.Get("Authorization") != "Bearer env-secret" {
					t.Fatalf("missing env bearer token")
				}
				if r.Method != http.MethodPost {
					t.Fatalf("expected POST, got %s", r.Method)
				}
				if got := r.URL.Query().Get("action"); got != action {
					t.Fatalf("expected action %q, got %q", action, got)
				}
				if got := r.URL.Query().Get("destination"); got != "/queue/orders" {
					t.Fatalf("unexpected destination %q", got)
				}
			}))
			defer server.Close()
			var stdout bytes.Buffer

			code := Run([]string{"--addr", server.URL, "queue", action, "/queue/orders"}, &stdout, &bytes.Buffer{}, "test")

			if code != 0 {
				t.Fatalf("expected exit code 0, got %d", code)
			}
			if requests != 1 {
				t.Fatalf("expected 1 request, got %d", requests)
			}
			if !strings.Contains(stdout.String(), action+"d default:/queue/orders") {
				t.Fatalf("expected %s output, got %q", action, stdout.String())
			}
		})
	}
}

func TestQueueActionCommandReportsMissingQueue(t *testing.T) {
	server := queueServer(t, http.StatusNotFound)
	defer server.Close()
	var stderr bytes.Buffer

	code := Run([]string{"--addr", server.URL, "queue", "pause", "/queue/missing"}, &bytes.Buffer{}, &stderr, "test")

	if code != 1 {
		t.Fatalf("expected exit code 1, got %d", code)
	}
	if !strings.Contains(stderr.String(), "404") {
		t.Fatalf("expected 404 in error, got %q", stderr.String())
	}
}

func TestRootCommandKeepsExistingSubcommands(t *testing.T) {
	command := newRootCommand(&bytes.Buffer{}, &bytes.Buffer{}, &bytes.Buffer{}, "test")

	want := map[string]bool{
		"monitor":     false,
		"perf-test":   false,
		"micro-bench": false,
		"queue":       false,
		"version":     false,
	}
	for _, sub := range command.Commands() {
		if _, ok := want[sub.Name()]; ok {
			want[sub.Name()] = true
		}
	}
	for name, found := range want {
		if !found {
			t.Fatalf("expected %q subcommand to stay registered", name)
		}
	}
}

func TestQueueCommandRegistersEveryAction(t *testing.T) {
	command := newRootCommand(&bytes.Buffer{}, &bytes.Buffer{}, &bytes.Buffer{}, "test")

	var queue *cobra.Command
	for _, sub := range command.Commands() {
		if sub.Name() == "queue" {
			queue = sub
		}
	}
	if queue == nil {
		t.Fatalf("expected queue command")
	}

	want := map[string]bool{"list": false, "create": false, "delete": false, "pause": false, "resume": false, "purge": false}
	for _, sub := range queue.Commands() {
		if _, ok := want[sub.Name()]; ok {
			want[sub.Name()] = true
		}
	}
	for name, found := range want {
		if !found {
			t.Fatalf("expected queue %q subcommand, got missing", name)
		}
	}
}

func TestQueueListFiltersByGroup(t *testing.T) {
	var query string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		_, _ = w.Write([]byte(`[{"group":"market","destination":"/queue/orders","size":1,"maxPendingBytes":40}]`))
	}))
	defer server.Close()
	var stdout bytes.Buffer

	code := Run([]string{"--addr", server.URL, "--no-color", "queue", "list", "--group", "market"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if query != "group=market" {
		t.Fatalf("expected a group filter, got %q", query)
	}
	if !strings.Contains(stdout.String(), "group filter: market") {
		t.Fatalf("expected the filter to be named, got %q", stdout.String())
	}
}

func TestQueueListWithoutGroupAsksForEveryGroup(t *testing.T) {
	var query string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		_, _ = w.Write([]byte(`[{"group":"default","destination":"/queue/orders"}]`))
	}))
	defer server.Close()

	code := Run([]string{"--addr", server.URL, "--no-color", "queue", "list"}, &bytes.Buffer{}, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if query != "" {
		t.Fatalf("expected no group parameter, got %q", query)
	}
}

func TestQueueChangeWithoutGroupTargetsTheDefaultGroup(t *testing.T) {
	var group string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		group = r.URL.Query().Get("group")
	}))
	defer server.Close()

	code := Run([]string{"--addr", server.URL, "queue", "pause", "/queue/orders"}, &bytes.Buffer{}, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if group != "default" {
		t.Fatalf("expected the default group, got %q", group)
	}
}

func TestQueueChangeSendsTheNamedGroup(t *testing.T) {
	var group string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		group = r.URL.Query().Get("group")
	}))
	defer server.Close()
	var stdout bytes.Buffer

	code := Run([]string{"--addr", server.URL, "queue", "purge", "/queue/orders", "--group", "market"}, &stdout, &bytes.Buffer{}, "test")

	if code != 0 {
		t.Fatalf("expected exit code 0, got %d", code)
	}
	if group != "market" {
		t.Fatalf("expected the market group, got %q", group)
	}
	if !strings.Contains(stdout.String(), "purged market:/queue/orders") {
		t.Fatalf("expected the group in the result, got %q", stdout.String())
	}
}

func TestQueueCommandRejectsAMalformedGroupBeforeRequesting(t *testing.T) {
	var requests int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
	}))
	defer server.Close()
	var stderr bytes.Buffer

	code := Run([]string{"--addr", server.URL, "queue", "delete", "/queue/orders", "--group", "bad/name"}, &bytes.Buffer{}, &stderr, "test")

	if code != 2 {
		t.Fatalf("expected exit code 2, got %d", code)
	}
	if requests != 0 {
		t.Fatalf("expected no request, got %d", requests)
	}
	if !strings.Contains(stderr.String(), "invalid group name") {
		t.Fatalf("expected an invalid group message, got %q", stderr.String())
	}
}

func TestViewRejectsAMalformedGroup(t *testing.T) {
	var stderr bytes.Buffer

	code := Run([]string{"--group", "bad/name", "--once"}, &bytes.Buffer{}, &stderr, "test")

	if code != 2 {
		t.Fatalf("expected exit code 2, got %d", code)
	}
	if !strings.Contains(stderr.String(), "invalid group name") {
		t.Fatalf("expected an invalid group message, got %q", stderr.String())
	}
}

func TestQueueListReportsAServerWithoutGroups(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[{"destination":"/queue/orders"}]`))
	}))
	defer server.Close()
	var stderr bytes.Buffer

	code := Run([]string{"--addr", server.URL, "queue", "list"}, &bytes.Buffer{}, &stderr, "test")

	if code != 1 {
		t.Fatalf("expected exit code 1, got %d", code)
	}
	if !strings.Contains(stderr.String(), "update the Titan server and CLI together") {
		t.Fatalf("expected a contract message, got %q", stderr.String())
	}
}
