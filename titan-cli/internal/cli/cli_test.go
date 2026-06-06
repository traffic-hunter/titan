package cli

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
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
	if !strings.Contains(stdout.String(), "Titan terminal monitor") {
		t.Fatalf("expected root help, got %q", stdout.String())
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
			"queues":[{"destination":"/queue/orders","size":5,"capacity":10,"paused":false}]
		}`))
	}))
}
