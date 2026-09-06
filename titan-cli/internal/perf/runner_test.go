package perf

import (
	"strings"
	"testing"
	"time"
)

func TestValidateRejectsPayloadWithoutMeasurementFields(t *testing.T) {
	config := Config{
		Host:              "127.0.0.1",
		Port:              7777,
		Destination:       "/queue/perf-test",
		WarmupMessages:    0,
		Messages:          1,
		Producers:         1,
		PayloadBytes:      measurementBytes - 1,
		ConnectTimeout:    time.Second,
		CompletionTimeout: time.Second,
	}

	if err := validate(config); err == nil {
		t.Fatal("expected payload validation error")
	}
}

func TestDecodeRunnerReportIgnoresJavaLogs(t *testing.T) {
	output := strings.Join([]string{
		"2026-09-06 INFO Event loop started",
		`TITAN_PERF_RESULT={"requested":10,"sent":10,"received":10,"failed":0,"elapsedNanos":2000000,"throughput":5000,"latencyP50Nanos":1000,"latencyP95Nanos":2000,"latencyP99Nanos":3000}`,
	}, "\n")

	report, err := decodeRunnerReport([]byte(output))
	if err != nil {
		t.Fatalf("decode report: %v", err)
	}
	if report.Received != 10 || report.Throughput != 5000 || report.P99 != 3*time.Microsecond {
		t.Fatalf("unexpected report: %+v", report)
	}
}

func TestReportReportsSuccessfulDelivery(t *testing.T) {
	report := Report{
		Requested:  4,
		Sent:       4,
		Received:   4,
		Elapsed:    2 * time.Second,
		Throughput: 2,
		P50:        2 * time.Millisecond,
		P99:        4 * time.Millisecond,
	}

	if !report.Successful() {
		t.Fatal("expected report to be successful")
	}
}
