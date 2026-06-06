package monitor

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSnapshotSendsBearerToken(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer secret" {
			t.Fatalf("missing bearer token")
		}
		_, _ = w.Write([]byte(`{"server":{"version":"test","uptimeMillis":1},"jvm":{"cpu":{},"heap":{},"thread":{}},"queues":[]}`))
	}))
	defer server.Close()

	snapshot, err := NewClient(server.URL, "secret").Snapshot(context.Background())

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if snapshot.Server.Version != "test" {
		t.Fatalf("expected test version, got %q", snapshot.Server.Version)
	}
}

func TestSnapshotReturnsHTTPError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer server.Close()

	_, err := NewClient(server.URL, "").Snapshot(context.Background())

	if err == nil {
		t.Fatalf("expected error")
	}
}
