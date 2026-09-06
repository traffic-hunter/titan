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
)

const measurementBytes = 20
const resultPrefix = "TITAN_PERF_RESULT="

type Config struct {
	Host              string
	Port              int
	Destination       string
	WarmupMessages    int
	Messages          int
	Producers         int
	PayloadBytes      int
	ConnectTimeout    time.Duration
	CompletionTimeout time.Duration
	RunnerPath        string
}

type Report struct {
	Requested  int
	Sent       int64
	Received   int64
	Failed     int
	Elapsed    time.Duration
	Throughput float64
	P50        time.Duration
	P95        time.Duration
	P99        time.Duration
}

type runnerReport struct {
	Requested       int     `json:"requested"`
	Sent            int64   `json:"sent"`
	Received        int64   `json:"received"`
	Failed          int     `json:"failed"`
	ElapsedNanos    int64   `json:"elapsedNanos"`
	Throughput      float64 `json:"throughput"`
	LatencyP50Nanos int64   `json:"latencyP50Nanos"`
	LatencyP95Nanos int64   `json:"latencyP95Nanos"`
	LatencyP99Nanos int64   `json:"latencyP99Nanos"`
}

func (r Report) Successful() bool {
	return r.Failed == 0 && r.Received == int64(r.Requested)
}

func Run(ctx context.Context, config Config) (Report, error) {
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
		"--destination", config.Destination,
		"--warmup-messages", strconv.Itoa(config.WarmupMessages),
		"--messages", strconv.Itoa(config.Messages),
		"--producers", strconv.Itoa(config.Producers),
		"--payload-bytes", strconv.Itoa(config.PayloadBytes),
		"--connect-timeout-millis", strconv.FormatInt(config.ConnectTimeout.Milliseconds(), 10),
		"--completion-timeout-millis", strconv.FormatInt(config.CompletionTimeout.Milliseconds(), 10),
	}
	command := exec.CommandContext(ctx, java, arguments...)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	command.Stdout = &stdout
	command.Stderr = &stderr
	if err := command.Run(); err != nil {
		reason := strings.TrimSpace(stderr.String())
		if reason == "" {
			reason = err.Error()
		}
		return Report{}, fmt.Errorf("Java performance runner failed: %s", reason)
	}

	result, err := decodeRunnerReport(stdout.Bytes())
	if err != nil {
		return Report{}, err
	}
	return result, nil
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
		Requested:  result.Requested,
		Sent:       result.Sent,
		Received:   result.Received,
		Failed:     result.Failed,
		Elapsed:    time.Duration(result.ElapsedNanos),
		Throughput: result.Throughput,
		P50:        time.Duration(result.LatencyP50Nanos),
		P95:        time.Duration(result.LatencyP95Nanos),
		P99:        time.Duration(result.LatencyP99Nanos),
	}, nil
}

func validate(config Config) error {
	if config.Host == "" {
		return fmt.Errorf("host must not be empty")
	}
	if config.Port <= 0 || config.Port > 65535 {
		return fmt.Errorf("port must be between 1 and 65535")
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
