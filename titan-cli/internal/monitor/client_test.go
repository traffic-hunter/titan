package monitor

import (
	"context"
	"errors"
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

	var httpErr HTTPError
	if !errors.As(err, &httpErr) {
		t.Fatalf("expected error")
	}
	if httpErr.StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", httpErr.StatusCode)
	}
}

func TestCreateAndDeleteQueueUseManagementEndpoint(t *testing.T) {
	var sawCreate bool
	var sawDelete bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer secret" {
			t.Fatalf("missing bearer token")
		}
		if r.URL.Path != "/titan/monitor/queues" {
			t.Fatalf("unexpected path %q", r.URL.Path)
		}
		switch r.Method {
		case http.MethodPost:
			sawCreate = true
			if r.URL.Query().Get("destination") != "/queue/orders" {
				t.Fatalf("unexpected destination %q", r.URL.Query().Get("destination"))
			}
			if r.URL.Query().Get("capacity") != "20" {
				t.Fatalf("unexpected capacity %q", r.URL.Query().Get("capacity"))
			}
			_, _ = w.Write([]byte(`{"destination":"/queue/orders","size":0,"capacity":20,"paused":false}`))
		case http.MethodDelete:
			sawDelete = true
			if r.URL.Query().Get("force") != "true" {
				t.Fatalf("unexpected force %q", r.URL.Query().Get("force"))
			}
			_, _ = w.Write([]byte(`{"status":"deleted","size":0}`))
		default:
			t.Fatalf("unexpected method %s", r.Method)
		}
	}))
	defer server.Close()

	client := NewClient(server.URL, "secret")
	queue, err := client.CreateQueue(context.Background(), "/queue/orders", 20)
	if err != nil {
		t.Fatalf("unexpected create error: %v", err)
	}
	if queue.Capacity != 20 {
		t.Fatalf("expected capacity 20, got %d", queue.Capacity)
	}
	if err := client.DeleteQueue(context.Background(), "/queue/orders", true); err != nil {
		t.Fatalf("unexpected delete error: %v", err)
	}
	if !sawCreate || !sawDelete {
		t.Fatalf("expected create and delete calls")
	}
}
