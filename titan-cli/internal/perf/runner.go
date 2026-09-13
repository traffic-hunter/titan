package perf

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
)

// The runner stamps a run identifier, a producer identifier, a sequence number, and the send
// timestamp into every payload.
const measurementBytes = 24
const resultPrefix = "TITAN_PERF_RESULT="

// Transports the runner can reach the broker over.
const (
	TransportTCP       = "tcp"
	TransportWebSocket = "websocket"
)

// How a completed send is read. Receipt mode learns whether the broker accepted the message;
// write mode only learns that the local write was submitted.
const (
	SendModeReceipt = "receipt"
	SendModeWrite   = "write"
)

// The server path a run is aimed at. The runner cannot tell them apart over STOMP, so the label
// travels with the result and keeps a direct run from being read as a dispatch guarantee.
const (
	PathDispatch = "dispatch"
	PathDirect   = "direct"
)

type Config struct {
	Host              string
	Port              int
	Transport         string
	WebSocketPath     string
	SendMode          string
	PathLabel         string
	Group             string
	Destination       string
	WarmupMessages    int
	Messages          int
	Producers         int
	PayloadBytes      int
	ConnectTimeout    time.Duration
	CompletionTimeout time.Duration
	RunnerPath        string

	// Where one run's raw output is kept, and what it is filed under.
	ResultsDir      string
	RunID           string
	Iteration       int
	FixtureManifest string
}

// Latency is one stage's distribution. It is absent when the stage produced no samples; a run that
// received nothing has no latency rather than a latency of zero.
type Latency struct {
	Samples int           `json:"samples"`
	P50     time.Duration `json:"p50"`
	P95     time.Duration `json:"p95"`
	P99     time.Duration `json:"p99"`
	Max     time.Duration `json:"max"`
}

// Report is what one run measured. A nil counter is a stage this runner cannot observe, which is
// never the same thing as a zero.
type Report struct {
	RunID       string
	SendMode    string
	Transport   string
	PathLabel   string
	Group       string
	Destination string
	Producers   int

	Requested    int
	Attempted    int
	NotAttempted int

	WriteSubmitted *int
	Written        *int
	Accepted       *int
	Rejected       int
	LocalNotSent   int
	Unknown        int

	Received            int
	Duplicates          int
	AcceptedNotReceived int
	Contradiction       int
	ForeignMessages     int
	MalformedMessages   int

	WarmupRequested int
	WarmupReceived  int

	CountsBalanced          bool
	CompletedBeforeDeadline bool
	ProducersStopped        bool
	CleanupErrors           []string

	Elapsed         time.Duration
	Throughput      float64
	DeliveryLatency *Latency
	ReceiptLatency  *Latency

	ResultsPath string
}

type runnerLatency struct {
	Samples  int   `json:"samples"`
	P50Nanos int64 `json:"p50Nanos"`
	P95Nanos int64 `json:"p95Nanos"`
	P99Nanos int64 `json:"p99Nanos"`
	MaxNanos int64 `json:"maxNanos"`
}

type runnerReport struct {
	RunID        string `json:"runId"`
	SendMode     string `json:"sendMode"`
	Transport    string `json:"transport"`
	PathLabel    string `json:"pathLabel"`
	Group        string `json:"group"`
	Destination  string `json:"destination"`
	Producers    int    `json:"producers"`
	PayloadBytes int    `json:"payloadBytes"`

	Requested    int `json:"requested"`
	Attempted    int `json:"attempted"`
	NotAttempted int `json:"notAttempted"`

	WriteSubmitted *int `json:"writeSubmitted"`
	Written        *int `json:"written"`
	Accepted       *int `json:"accepted"`
	Rejected       int  `json:"rejected"`
	LocalNotSent   int  `json:"localNotSent"`
	Unknown        int  `json:"unknown"`

	Received            int `json:"received"`
	Duplicates          int `json:"duplicates"`
	AcceptedNotReceived int `json:"acceptedNotReceived"`
	Contradiction       int `json:"contradiction"`
	ForeignMessages     int `json:"foreignMessages"`
	MalformedMessages   int `json:"malformedMessages"`

	WarmupRequested int `json:"warmupRequested"`
	WarmupReceived  int `json:"warmupReceived"`

	CountsBalanced          bool     `json:"countsBalanced"`
	CompletedBeforeDeadline bool     `json:"completedBeforeDeadline"`
	ProducersStopped        bool     `json:"producersStopped"`
	CleanupErrors           []string `json:"cleanupErrors"`

	ElapsedNanos    int64          `json:"elapsedNanos"`
	Throughput      float64        `json:"throughput"`
	DeliveryLatency *runnerLatency `json:"deliveryLatency"`
	ReceiptLatency  *runnerLatency `json:"receiptLatency"`

	Environment json.RawMessage `json:"environment"`
}

// Manifest records what a run was, so a number can be read back with the conditions that produced
// it. A result without its conditions is not evidence.
type Manifest struct {
	RunID      string          `json:"runId"`
	Iteration  int             `json:"iteration"`
	StartedAt  string          `json:"startedAt"`
	FinishedAt string          `json:"finishedAt"`
	CLIVersion string          `json:"cliVersion"`
	Commit     string          `json:"commit"`
	Config     Config          `json:"config"`
	Fixture    json.RawMessage `json:"fixture"`
	Report     json.RawMessage `json:"report"`
}

// Successful reports whether the run met the normal-load bar: every requested message accepted and
// received exactly once, nothing refused, nothing unexplained, and a clean shutdown.
func (r Report) Successful() bool {
	if r.SendMode == SendModeReceipt {
		if r.Accepted == nil || *r.Accepted != r.Requested {
			return false
		}
	} else if r.SendMode == SendModeWrite {
		if r.WriteSubmitted == nil || *r.WriteSubmitted != r.Requested {
			return false
		}
	} else {
		return false
	}
	if r.Requested != r.Received || r.Attempted != r.Requested {
		return false
	}
	if r.NotAttempted > 0 || r.Rejected > 0 || r.LocalNotSent > 0 || r.Unknown > 0 {
		return false
	}
	if r.Duplicates > 0 || r.AcceptedNotReceived > 0 || r.Contradiction > 0 {
		return false
	}
	if r.ForeignMessages > 0 || r.MalformedMessages > 0 {
		return false
	}
	if r.WarmupReceived != r.WarmupRequested {
		return false
	}
	if !r.CountsBalanced || !r.CompletedBeforeDeadline || !r.ProducersStopped {
		return false
	}
	if len(r.CleanupErrors) > 0 {
		return false
	}
	if r.Accepted != nil && *r.Accepted != r.Requested {
		return false
	}
	return true
}

func Run(ctx context.Context, config Config) (Report, error) {
	// The group is resolved once here so the runner always receives an explicit
	// name and every publish and subscribe in the run targets the same queue.
	group, err := monitor.NormalizeGroup(config.Group)
	if err != nil {
		return Report{}, err
	}
	config.Group = group
	config = withDefaults(config)

	if err := validate(config); err != nil {
		return Report{}, err
	}
	runner, err := resolveRunner(config.RunnerPath)
	if err != nil {
		return Report{}, err
	}
	java, err := javaExecutable()
	if err != nil {
		return Report{}, err
	}

	arguments := []string{
		"-jar", runner,
		"--host", config.Host,
		"--port", strconv.Itoa(config.Port),
		"--transport", config.Transport,
		"--websocket-path", config.WebSocketPath,
		"--send-mode", config.SendMode,
		"--path-label", config.PathLabel,
		"--group", config.Group,
		"--destination", config.Destination,
		"--warmup-messages", strconv.Itoa(config.WarmupMessages),
		"--messages", strconv.Itoa(config.Messages),
		"--producers", strconv.Itoa(config.Producers),
		"--payload-bytes", strconv.Itoa(config.PayloadBytes),
		"--connect-timeout-millis", strconv.FormatInt(config.ConnectTimeout.Milliseconds(), 10),
		"--completion-timeout-millis", strconv.FormatInt(config.CompletionTimeout.Milliseconds(), 10),
	}
	startedAt := time.Now().UTC()
	command := exec.CommandContext(ctx, java, arguments...)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	command.Stdout = &stdout
	command.Stderr = &stderr
	runErr := command.Run()
	finishedAt := time.Now().UTC()

	// The raw output is kept even when the runner failed: a run that died halfway is a result too.
	resultsPath, saveErr := save(config, startedAt, finishedAt, stdout.Bytes(), stderr.Bytes())
	if runErr != nil {
		reason := strings.TrimSpace(stderr.String())
		if reason == "" {
			reason = runErr.Error()
		}
		return Report{}, fmt.Errorf("Java performance runner failed: %s", reason)
	}
	if saveErr != nil {
		return Report{}, saveErr
	}

	result, err := decodeRunnerReport(stdout.Bytes())
	if err != nil {
		return Report{}, err
	}
	result.Group = config.Group
	result.Destination = config.Destination
	result.ResultsPath = resultsPath
	return result, nil
}

func withDefaults(config Config) Config {
	if config.Transport == "" {
		config.Transport = TransportTCP
	}
	if config.WebSocketPath == "" {
		config.WebSocketPath = "/stomp"
	}
	if config.SendMode == "" {
		config.SendMode = SendModeReceipt
	}
	if config.PathLabel == "" {
		config.PathLabel = PathDispatch
	}
	return config
}

func decodeRunnerReport(output []byte) (Report, error) {
	var resultLine []byte
	for _, line := range bytes.Split(output, []byte{'\n'}) {
		if bytes.HasPrefix(line, []byte(resultPrefix)) {
			resultLine = bytes.TrimSpace(bytes.TrimPrefix(line, []byte(resultPrefix)))
		}
	}
	if len(resultLine) == 0 {
		return Report{}, fmt.Errorf("Java performance runner did not return a result")
	}

	var result runnerReport
	if err := json.Unmarshal(resultLine, &result); err != nil {
		return Report{}, fmt.Errorf("decode Java performance result: %w", err)
	}
	return Report{
		RunID:       result.RunID,
		SendMode:    result.SendMode,
		Transport:   result.Transport,
		PathLabel:   result.PathLabel,
		Group:       result.Group,
		Destination: result.Destination,
		Producers:   result.Producers,

		Requested:    result.Requested,
		Attempted:    result.Attempted,
		NotAttempted: result.NotAttempted,

		WriteSubmitted: result.WriteSubmitted,
		Written:        result.Written,
		Accepted:       result.Accepted,
		Rejected:       result.Rejected,
		LocalNotSent:   result.LocalNotSent,
		Unknown:        result.Unknown,

		Received:            result.Received,
		Duplicates:          result.Duplicates,
		AcceptedNotReceived: result.AcceptedNotReceived,
		Contradiction:       result.Contradiction,
		ForeignMessages:     result.ForeignMessages,
		MalformedMessages:   result.MalformedMessages,

		WarmupRequested: result.WarmupRequested,
		WarmupReceived:  result.WarmupReceived,

		CountsBalanced:          result.CountsBalanced,
		CompletedBeforeDeadline: result.CompletedBeforeDeadline,
		ProducersStopped:        result.ProducersStopped,
		CleanupErrors:           result.CleanupErrors,

		Elapsed:         time.Duration(result.ElapsedNanos),
		Throughput:      result.Throughput,
		DeliveryLatency: toLatency(result.DeliveryLatency),
		ReceiptLatency:  toLatency(result.ReceiptLatency),
	}, nil
}

func toLatency(raw *runnerLatency) *Latency {
	if raw == nil {
		return nil
	}
	return &Latency{
		Samples: raw.Samples,
		P50:     time.Duration(raw.P50Nanos),
		P95:     time.Duration(raw.P95Nanos),
		P99:     time.Duration(raw.P99Nanos),
		Max:     time.Duration(raw.MaxNanos),
	}
}

// save writes the raw runner output and the run manifest, and returns the directory holding them.
func save(config Config, startedAt time.Time, finishedAt time.Time, stdout []byte, stderr []byte) (string, error) {
	if config.ResultsDir == "" {
		return "", nil
	}

	runID := config.RunID
	if runID == "" {
		runID = startedAt.Format("20060102-150405")
	}
	directory := filepath.Join(config.ResultsDir, runID)
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return "", fmt.Errorf("create results directory: %w", err)
	}

	name := "run"
	if config.Iteration > 0 {
		name = fmt.Sprintf("run-%d", config.Iteration)
	}
	if err := os.WriteFile(filepath.Join(directory, name+".log"), append(stdout, stderr...), 0o644); err != nil {
		return "", fmt.Errorf("write runner log: %w", err)
	}

	manifest := Manifest{
		RunID:      runID,
		Iteration:  config.Iteration,
		StartedAt:  startedAt.Format(time.RFC3339Nano),
		FinishedAt: finishedAt.Format(time.RFC3339Nano),
		Commit:     commit(),
		Config:     config,
		Fixture:    readFixtureManifest(config.FixtureManifest),
		Report:     resultLine(stdout),
	}
	encoded, err := json.MarshalIndent(manifest, "", "  ")
	if err != nil {
		return "", fmt.Errorf("encode run manifest: %w", err)
	}
	if err := os.WriteFile(filepath.Join(directory, name+".json"), encoded, 0o644); err != nil {
		return "", fmt.Errorf("write run manifest: %w", err)
	}
	return directory, nil
}

func resultLine(output []byte) json.RawMessage {
	for _, line := range bytes.Split(output, []byte{'\n'}) {
		if bytes.HasPrefix(line, []byte(resultPrefix)) {
			return json.RawMessage(bytes.TrimSpace(bytes.TrimPrefix(line, []byte(resultPrefix))))
		}
	}
	return json.RawMessage("null")
}

// readFixtureManifest copies the fixture's own description of itself into the run manifest. A
// missing or unreadable file leaves the field null rather than inventing the server's settings.
func readFixtureManifest(path string) json.RawMessage {
	if path == "" {
		return json.RawMessage("null")
	}
	content, err := os.ReadFile(path)
	if err != nil || !json.Valid(content) {
		return json.RawMessage("null")
	}
	return json.RawMessage(bytes.TrimSpace(content))
}

func commit() string {
	output, err := exec.Command("git", "rev-parse", "HEAD").Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

func validate(config Config) error {
	if config.Host == "" {
		return fmt.Errorf("host must not be empty")
	}
	if config.Port <= 0 || config.Port > 65535 {
		return fmt.Errorf("port must be between 1 and 65535")
	}
	if config.Transport != TransportTCP && config.Transport != TransportWebSocket {
		return fmt.Errorf("transport must be %s or %s", TransportTCP, TransportWebSocket)
	}
	if config.SendMode != SendModeReceipt && config.SendMode != SendModeWrite {
		return fmt.Errorf("send mode must be %s or %s", SendModeReceipt, SendModeWrite)
	}
	if config.PathLabel != PathDispatch && config.PathLabel != PathDirect {
		return fmt.Errorf("path label must be %s or %s", PathDispatch, PathDirect)
	}
	if config.Destination == "" {
		return fmt.Errorf("destination must not be empty")
	}
	if strings.ContainsAny(config.Destination, "\r\n\x00") {
		return fmt.Errorf("destination contains an invalid STOMP character")
	}
	if config.WarmupMessages < 0 {
		return fmt.Errorf("warm-up messages must not be negative")
	}
	if config.Messages <= 0 {
		return fmt.Errorf("messages must be greater than 0")
	}
	if config.Producers <= 0 {
		return fmt.Errorf("producers must be greater than 0")
	}
	if config.PayloadBytes < measurementBytes {
		return fmt.Errorf("payload bytes must be at least %d", measurementBytes)
	}
	if config.ConnectTimeout <= 0 || config.CompletionTimeout <= 0 {
		return fmt.Errorf("timeouts must be greater than 0")
	}
	if config.Iteration < 0 {
		return fmt.Errorf("iteration must not be negative")
	}
	return nil
}

func resolveRunner(configured string) (string, error) {
	if configured != "" {
		return existingFile(configured)
	}
	if configured = os.Getenv("TITAN_PERF_RUNNER"); configured != "" {
		return existingFile(configured)
	}
	if executable, err := os.Executable(); err == nil {
		bundled := filepath.Join(filepath.Dir(executable), "lib", "titan-perf-runner.jar")
		if path, err := existingFile(bundled); err == nil {
			return path, nil
		}
	}

	directory, err := os.Getwd()
	if err == nil {
		for {
			matches, _ := filepath.Glob(filepath.Join(
				directory,
				"benchmark",
				"perf-test",
				"build",
				"libs",
				"titan-perf-runner-*.jar",
			))
			if len(matches) > 0 {
				return matches[0], nil
			}
			parent := filepath.Dir(directory)
			if parent == directory {
				break
			}
			directory = parent
		}
	}
	return "", fmt.Errorf("Titan performance runner was not found; build :benchmark:perf-test:shadowJar or set TITAN_PERF_RUNNER")
}

func existingFile(path string) (string, error) {
	info, err := os.Stat(path)
	if err != nil {
		return "", fmt.Errorf("performance runner %q: %w", path, err)
	}
	if info.IsDir() {
		return "", fmt.Errorf("performance runner %q is not a file", path)
	}
	return path, nil
}

func javaExecutable() (string, error) {
	name := "java"
	if runtime.GOOS == "windows" {
		name = "java.exe"
	}
	if javaHome := os.Getenv("JAVA_HOME"); javaHome != "" {
		path := filepath.Join(javaHome, "bin", name)
		if _, err := os.Stat(path); err == nil {
			return path, nil
		}
	}
	path, err := exec.LookPath(name)
	if err != nil {
		return "", fmt.Errorf("java runtime was not found")
	}
	return path, nil
}
