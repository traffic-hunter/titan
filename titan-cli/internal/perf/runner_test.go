package perf

import (
	"bytes"
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func validConfig() Config {
	return Config{
		Host:              "127.0.0.1",
		Port:              7777,
		Transport:         TransportTCP,
		WebSocketPath:     "/stomp",
		SendMode:          SendModeReceipt,
		PathLabel:         PathDispatch,
		Destination:       "/queue/perf-test",
		WarmupMessages:    0,
		Messages:          1,
		Producers:         1,
		PayloadBytes:      measurementBytes,
		ConnectTimeout:    time.Second,
		CompletionTimeout: time.Second,
	}
}

func TestValidateRejectsPayloadWithoutMeasurementFields(t *testing.T) {
	config := validConfig()
	config.PayloadBytes = measurementBytes - 1

	if err := validate(config); err == nil {
		t.Fatal("expected payload validation error")
	}
}

func TestValidateRejectsAModeTheRunnerCannotHonour(t *testing.T) {
	transport := validConfig()
	transport.Transport = "udp"
	if err := validate(transport); err == nil {
		t.Fatal("expected a transport error")
	}

	sendMode := validConfig()
	sendMode.SendMode = "ack"
	if err := validate(sendMode); err == nil {
		t.Fatal("expected a send mode error")
	}

	pathLabel := validConfig()
	pathLabel.PathLabel = "queue"
	if err := validate(pathLabel); err == nil {
		t.Fatal("expected a path label error")
	}
}

func TestWithDefaultsMeasuresAcceptanceOverTCPOnTheDispatchPath(t *testing.T) {
	config := withDefaults(Config{})

	if config.Transport != TransportTCP || config.SendMode != SendModeReceipt {
		t.Fatalf("unexpected defaults: %+v", config)
	}
	if config.PathLabel != PathDispatch || config.WebSocketPath != "/stomp" {
		t.Fatalf("unexpected defaults: %+v", config)
	}
}

func TestDecodeRunnerReportIgnoresJavaLogs(t *testing.T) {
	output := strings.Join([]string{
		"2026-09-06 INFO Event loop started",
		successLine(),
	}, "\n")

	report, err := decodeRunnerReport([]byte(output))
	if err != nil {
		t.Fatalf("decode report: %v", err)
	}
	if report.Received != 10 || report.Throughput != 5000 {
		t.Fatalf("unexpected report: %+v", report)
	}
	if report.Accepted == nil || *report.Accepted != 10 {
		t.Fatalf("expected an accepted count: %+v", report.Accepted)
	}
	if report.DeliveryLatency == nil || report.DeliveryLatency.P99 != 3*time.Microsecond {
		t.Fatalf("unexpected delivery latency: %+v", report.DeliveryLatency)
	}
}

func TestDecodeRunnerReportKeepsUnmeasuredStagesNil(t *testing.T) {
	report, err := decodeRunnerReport([]byte(writeModeLine()))
	if err != nil {
		t.Fatalf("decode report: %v", err)
	}

	// A stage the runner cannot see must stay absent. Reading it as a zero would turn "unknown"
	// into "nothing was written".
	if report.Written != nil {
		t.Fatalf("expected written to be unmeasured: %+v", report.Written)
	}
	if report.Accepted != nil {
		t.Fatalf("expected acceptance to be unmeasured in write mode: %+v", report.Accepted)
	}
	if report.WriteSubmitted == nil || *report.WriteSubmitted != 10 {
		t.Fatalf("expected a write submitted count: %+v", report.WriteSubmitted)
	}
	if report.ReceiptLatency != nil {
		t.Fatalf("expected no receipt latency in write mode: %+v", report.ReceiptLatency)
	}
}

func TestReportIsSuccessfulOnlyWhenEveryMessageIsAccountedFor(t *testing.T) {
	report, err := decodeRunnerReport([]byte(successLine()))
	if err != nil {
		t.Fatalf("decode report: %v", err)
	}
	if !report.Successful() {
		t.Fatal("expected report to be successful")
	}

	for name, damage := range map[string]func(*Report){
		"missing acceptance":     func(r *Report) { r.Accepted = nil },
		"unknown send mode":      func(r *Report) { r.SendMode = "other" },
		"a duplicate":            func(r *Report) { r.Duplicates = 1 },
		"an unknown attempt":     func(r *Report) { r.Unknown = 1 },
		"a rejection":            func(r *Report) { r.Rejected = 1 },
		"a message never sent":   func(r *Report) { r.LocalNotSent = 1 },
		"a missing delivery":     func(r *Report) { r.AcceptedNotReceived = 1 },
		"a contradiction":        func(r *Report) { r.Contradiction = 1 },
		"a stray message":        func(r *Report) { r.ForeignMessages = 1 },
		"unbalanced counts":      func(r *Report) { r.CountsBalanced = false },
		"a missed deadline":      func(r *Report) { r.CompletedBeforeDeadline = false },
		"a producer still alive": func(r *Report) { r.ProducersStopped = false },
		"a cleanup failure":      func(r *Report) { r.CleanupErrors = []string{"boom"} },
		"a short warm-up":        func(r *Report) { r.WarmupReceived = 0; r.WarmupRequested = 1 },
	} {
		damaged := report
		damage(&damaged)
		if damaged.Successful() {
			t.Fatalf("expected %s to fail the run", name)
		}
	}
}

func TestSaveWritesTheRawResultAndTheRunManifest(t *testing.T) {
	directory := t.TempDir()
	fixture := filepath.Join(directory, "fixture.json")
	if err := os.WriteFile(fixture, []byte(`{"port":1234,"path":"dispatch"}`), 0o644); err != nil {
		t.Fatalf("write fixture manifest: %v", err)
	}

	config := validConfig()
	config.ResultsDir = filepath.Join(directory, "stability")
	config.RunID = "baseline"
	config.Iteration = 2
	config.FixtureManifest = fixture

	started := time.Now().UTC()
	path, err := save(config, started, started.Add(time.Second), []byte("log line\n"+successLine()+"\n"), []byte("stderr line\n"))
	if err != nil {
		t.Fatalf("save: %v", err)
	}
	if path != filepath.Join(config.ResultsDir, "baseline") {
		t.Fatalf("unexpected results path: %s", path)
	}

	content, err := os.ReadFile(filepath.Join(path, "run-2.json"))
	if err != nil {
		t.Fatalf("read manifest: %v", err)
	}
	var manifest Manifest
	if err := json.Unmarshal(content, &manifest); err != nil {
		t.Fatalf("decode manifest: %v", err)
	}
	if manifest.RunID != "baseline" || manifest.Iteration != 2 {
		t.Fatalf("unexpected manifest: %+v", manifest)
	}
	if !strings.Contains(compact(t, manifest.Fixture), `"port":1234`) {
		t.Fatalf("expected the fixture settings to be kept: %s", manifest.Fixture)
	}
	if !strings.Contains(compact(t, manifest.Report), `"received":10`) {
		t.Fatalf("expected the raw report to be kept: %s", manifest.Report)
	}

	log, err := os.ReadFile(filepath.Join(path, "run-2.log"))
	if err != nil {
		t.Fatalf("read log: %v", err)
	}
	if !strings.Contains(string(log), "stderr line") {
		t.Fatalf("expected the runner output to be kept: %s", log)
	}
}

func TestSaveLeavesTheFixtureNullWhenItIsNotGiven(t *testing.T) {
	config := validConfig()
	config.ResultsDir = t.TempDir()
	config.RunID = "no-fixture"
	config.FixtureManifest = filepath.Join(config.ResultsDir, "missing.json")

	started := time.Now().UTC()
	path, err := save(config, started, started, []byte(successLine()+"\n"), nil)
	if err != nil {
		t.Fatalf("save: %v", err)
	}

	content, err := os.ReadFile(filepath.Join(path, "run.json"))
	if err != nil {
		t.Fatalf("read manifest: %v", err)
	}
	// An unreadable fixture manifest must leave the server's settings unstated rather than guessed.
	if !strings.Contains(string(content), `"fixture": null`) {
		t.Fatalf("expected a null fixture: %s", content)
	}
}

func TestRunRejectsAMalformedGroupBeforeLookingForTheRunner(t *testing.T) {
	config := validConfig()
	config.Group = "bad/name"

	_, err := Run(context.Background(), config)

	if err == nil {
		t.Fatalf("expected a group error")
	}
	if !strings.Contains(err.Error(), "invalid group name") {
		t.Fatalf("unexpected error: %v", err)
	}
}

// compact removes the indentation the manifest is written with so a field can be matched by value.
func compact(t *testing.T, raw json.RawMessage) string {
	t.Helper()
	var buffer bytes.Buffer
	if err := json.Compact(&buffer, raw); err != nil {
		t.Fatalf("compact json: %v", err)
	}
	return buffer.String()
}

func successLine() string {
	return resultPrefix + `{"runId":"abc","sendMode":"receipt","transport":"tcp","pathLabel":"dispatch",` +
		`"group":"default","destination":"/queue/perf","producers":1,"payloadBytes":1024,` +
		`"requested":10,"attempted":10,"notAttempted":0,` +
		`"writeSubmitted":null,"written":null,"accepted":10,"rejected":0,"localNotSent":0,"unknown":0,` +
		`"received":10,"duplicates":0,"acceptedNotReceived":0,"contradiction":0,` +
		`"foreignMessages":0,"malformedMessages":0,"warmupRequested":0,"warmupReceived":0,` +
		`"countsBalanced":true,"completedBeforeDeadline":true,"producersStopped":true,"cleanupErrors":[],` +
		`"elapsedNanos":2000000,"throughput":5000,` +
		`"deliveryLatency":{"samples":10,"p50Nanos":1000,"p95Nanos":2000,"p99Nanos":3000,"maxNanos":4000},` +
		`"receiptLatency":{"samples":10,"p50Nanos":1000,"p95Nanos":2000,"p99Nanos":3000,"maxNanos":4000},` +
		`"environment":{"javaVersion":"21.0.11"}}`
}

func writeModeLine() string {
	return resultPrefix + `{"runId":"abc","sendMode":"write","transport":"tcp","pathLabel":"dispatch",` +
		`"group":"default","destination":"/queue/perf","producers":1,"payloadBytes":1024,` +
		`"requested":10,"attempted":10,"notAttempted":0,` +
		`"writeSubmitted":10,"written":null,"accepted":null,"rejected":0,"localNotSent":0,"unknown":0,` +
		`"received":10,"duplicates":0,"acceptedNotReceived":0,"contradiction":0,` +
		`"foreignMessages":0,"malformedMessages":0,"warmupRequested":0,"warmupReceived":0,` +
		`"countsBalanced":true,"completedBeforeDeadline":true,"producersStopped":true,"cleanupErrors":[],` +
		`"elapsedNanos":2000000,"throughput":5000,` +
		`"deliveryLatency":{"samples":10,"p50Nanos":1000,"p95Nanos":2000,"p99Nanos":3000,"maxNanos":4000},` +
		`"receiptLatency":null,"environment":{}}`
}
