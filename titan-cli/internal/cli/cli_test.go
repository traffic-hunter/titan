package cli

import (
	"bytes"
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

func TestRunRejectsUnknownMonitorCommand(t *testing.T) {
	var stderr bytes.Buffer

	code := Run([]string{"monitor", "unknown"}, &bytes.Buffer{}, &stderr, "test")

	if code != 2 {
		t.Fatalf("expected exit code 2, got %d", code)
	}
	if !strings.Contains(stderr.String(), "unknown monitor command") {
		t.Fatalf("expected unknown monitor command message, got %q", stderr.String())
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
