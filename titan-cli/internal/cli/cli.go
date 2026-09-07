package cli

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/charmbracelet/huh"
	"github.com/spf13/cobra"
	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
	"github.com/traffic-hunter/titan/titan-cli/internal/perf"
	"github.com/traffic-hunter/titan/titan-cli/internal/render"
)

const defaultAddr = "http://localhost:7777"

type exitError struct {
	code int
	err  error
}

func (e exitError) Error() string {
	return e.err.Error()
}

type viewOptions struct {
	addr     string
	token    string
	interval time.Duration
	timeout  time.Duration
	view     string
	noClear  bool
	noColor  bool
	once     bool
}

type queueOptions struct {
	addr            string
	token           string
	timeout         time.Duration
	noColor         bool
	maxPendingBytes int64
	force           bool
}

type perfOptions struct {
	host              string
	port              int
	destination       string
	warmupMessages    int
	messages          int
	producers         int
	payloadBytes      int
	connectTimeout    time.Duration
	completionTimeout time.Duration
	runnerPath        string
}

func Run(args []string, stdout io.Writer, stderr io.Writer, version string) int {
	return RunWithInput(args, os.Stdin, stdout, stderr, version)
}

func RunWithInput(args []string, stdin io.Reader, stdout io.Writer, stderr io.Writer, version string) int {
	if len(args) == 0 && interactive(stdin, stdout) {
		for {
			selected, err := selectTool(stdin, stdout, version)
			if errors.Is(err, huh.ErrUserAborted) {
				return 0
			}
			if err != nil {
				fmt.Fprintln(stderr, err)
				return 2
			}

			// Management runs its own submenu and comes back here, so the main
			// menu is shown again instead of exiting the process.
			if selected == "management" {
				if err := runManagement(stdin, stdout, false); err != nil {
					fmt.Fprintln(stderr, err)
					return 2
				}
				continue
			}

			args = []string{selected}
			if selected == "perf-test" {
				args, err = selectPerfSettings(stdin, stdout)
				if errors.Is(err, huh.ErrUserAborted) {
					return 0
				}
				if err != nil {
					fmt.Fprintln(stderr, err)
					return 2
				}
			}
			break
		}
	}

	command := newRootCommand(stdin, stdout, stderr, version)
	command.SetArgs(args)
	if err := command.Execute(); err != nil {
		var exit exitError
		if errors.As(err, &exit) {
			fmt.Fprintln(stderr, exit.err)
			return exit.code
		}
		fmt.Fprintln(stderr, err)
		return 2
	}
	return 0
}

func newRootCommand(stdin io.Reader, stdout io.Writer, stderr io.Writer, version string) *cobra.Command {
	options := &viewOptions{}
	command := &cobra.Command{
		Use:           "titan",
		Short:         "Titan command-line tools",
		SilenceUsage:  true,
		SilenceErrors: true,
		CompletionOptions: cobra.CompletionOptions{
			DisableDefaultCmd: true,
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			return runView(cmd.Context(), stdout, options)
		},
	}
	command.SetOut(stdout)
	command.SetErr(stderr)
	command.PersistentFlags().StringVar(&options.addr, "addr", defaultAddr, "Titan monitor HTTP address")
	command.PersistentFlags().StringVar(&options.token, "token", "", "Titan monitor bearer token")
	command.PersistentFlags().DurationVar(&options.timeout, "timeout", 5*time.Second, "HTTP request timeout")
	command.PersistentFlags().BoolVar(&options.noColor, "no-color", false, "Render without ANSI colors")
	command.Flags().DurationVar(&options.interval, "interval", time.Second, "Polling interval")
	command.Flags().StringVar(&options.view, "view", "overview", "Initial view: overview, queues, or jvm")
	command.Flags().BoolVar(&options.noClear, "no-clear", false, "Render without clearing the terminal")
	command.Flags().BoolVar(&options.once, "once", false, "Render one frame and exit")
	command.AddCommand(monitorCommand(stdout, options))
	command.AddCommand(queueCommand(stdout, options))
	command.AddCommand(perfCommand(stdout))
	command.AddCommand(microBenchmarkCommand(stdin, stdout, stderr))
	command.AddCommand(versionCommand(stdout, version))
	return command
}

func monitorCommand(stdout io.Writer, options *viewOptions) *cobra.Command {
	command := &cobra.Command{
		Use:           "monitor",
		Short:         "Open the live Titan monitor",
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runView(cmd.Context(), stdout, options)
		},
	}
	command.Flags().DurationVar(&options.interval, "interval", time.Second, "Polling interval")
	command.Flags().StringVar(&options.view, "view", "overview", "Initial view: overview, queues, or jvm")
	command.Flags().BoolVar(&options.noClear, "no-clear", false, "Render without clearing the terminal")
	command.Flags().BoolVar(&options.once, "once", false, "Render one frame and exit")
	return command
}

func perfCommand(stdout io.Writer) *cobra.Command {
	options := &perfOptions{}
	command := &cobra.Command{
		Use:           "perf-test",
		Aliases:       []string{"perf"},
		Short:         "Run an end-to-end Titan performance test",
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			report, err := perf.Run(cmd.Context(), perf.Config{
				Host:              options.host,
				Port:              options.port,
				Destination:       options.destination,
				WarmupMessages:    options.warmupMessages,
				Messages:          options.messages,
				Producers:         options.producers,
				PayloadBytes:      options.payloadBytes,
				ConnectTimeout:    options.connectTimeout,
				CompletionTimeout: options.completionTimeout,
				RunnerPath:        options.runnerPath,
			})
			if err != nil {
				return exitError{code: 1, err: err}
			}
			printPerfReport(stdout, report)
			if !report.Successful() {
				return exitError{code: 1, err: fmt.Errorf("performance test did not deliver every message")}
			}
			return nil
		},
	}
	command.Flags().StringVar(&options.host, "host", "127.0.0.1", "Titan STOMP host")
	command.Flags().IntVar(&options.port, "port", 7777, "Titan STOMP port")
	command.Flags().StringVar(&options.destination, "destination", "/queue/perf-test", "STOMP destination")
	command.Flags().IntVar(&options.warmupMessages, "warmup-messages", 1_000, "Number of warm-up messages")
	command.Flags().IntVar(&options.messages, "messages", 10_000, "Number of messages")
	command.Flags().IntVar(&options.producers, "producers", 1, "Concurrent producer connections")
	command.Flags().IntVar(&options.payloadBytes, "payload-bytes", 1_024, "Payload size in bytes")
	command.Flags().DurationVar(&options.connectTimeout, "connect-timeout", 5*time.Second, "Connection timeout")
	command.Flags().DurationVar(&options.completionTimeout, "completion-timeout", 30*time.Second, "Overall test timeout")
	command.Flags().StringVar(&options.runnerPath, "runner", "", "Path to the Titan Java performance runner")
	return command
}

func printPerfReport(output io.Writer, report perf.Report) {
	fmt.Fprintln(output, "Titan performance test")
	fmt.Fprintf(output, "  requested  : %d\n", report.Requested)
	fmt.Fprintf(output, "  sent       : %d\n", report.Sent)
	fmt.Fprintf(output, "  received   : %d\n", report.Received)
	fmt.Fprintf(output, "  failed     : %d\n", report.Failed)
	fmt.Fprintf(output, "  elapsed    : %.3f s\n", report.Elapsed.Seconds())
	fmt.Fprintf(output, "  throughput : %.2f msg/s\n", report.Throughput)
	if report.Received > 0 {
		fmt.Fprintf(output, "  latency p50: %.3f ms\n", float64(report.P50.Microseconds())/1_000)
		fmt.Fprintf(output, "  latency p95: %.3f ms\n", float64(report.P95.Microseconds())/1_000)
		fmt.Fprintf(output, "  latency p99: %.3f ms\n", float64(report.P99.Microseconds())/1_000)
	}
}

func microBenchmarkCommand(stdin io.Reader, stdout io.Writer, stderr io.Writer) *cobra.Command {
	return &cobra.Command{
		Use:           "micro-bench",
		Short:         "Run the JMH microbenchmarks",
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			root, err := titanProjectRoot()
			if err != nil {
				return exitError{code: 1, err: err}
			}
			wrapper := "gradlew"
			if runtime.GOOS == "windows" {
				wrapper = "gradlew.bat"
			}
			benchmark := exec.CommandContext(cmd.Context(), filepath.Join(root, wrapper), ":benchmark:jmh:jmh")
			benchmark.Dir = root
			benchmark.Stdin = stdin
			benchmark.Stdout = stdout
			benchmark.Stderr = stderr
			if err := benchmark.Run(); err != nil {
				return exitError{code: 1, err: fmt.Errorf("run JMH benchmarks: %w", err)}
			}
			return nil
		},
	}
}

func titanProjectRoot() (string, error) {
	directory, err := os.Getwd()
	if err != nil {
		return "", err
	}
	for {
		wrapper := filepath.Join(directory, "gradlew")
		benchmark := filepath.Join(directory, "benchmark", "jmh", "build.gradle.kts")
		if _, wrapperError := os.Stat(wrapper); wrapperError == nil {
			if _, benchmarkError := os.Stat(benchmark); benchmarkError == nil {
				return directory, nil
			}
		}
		parent := filepath.Dir(directory)
		if parent == directory {
			return "", fmt.Errorf("micro-bench requires a Titan source checkout")
		}
		directory = parent
	}
}

func interactive(stdin io.Reader, stdout io.Writer) bool {
	input, inputOK := stdin.(*os.File)
	output, outputOK := stdout.(*os.File)
	if !inputOK || !outputOK {
		return false
	}
	inputInfo, inputError := input.Stat()
	outputInfo, outputError := output.Stat()
	return inputError == nil && outputError == nil &&
		inputInfo.Mode()&os.ModeCharDevice != 0 && outputInfo.Mode()&os.ModeCharDevice != 0
}

func selectTool(stdin io.Reader, stdout io.Writer, version string) (string, error) {
	render.Banner(stdout, version, render.Options{Color: true})
	selected := "monitor"
	theme := huh.ThemeCharm()
	theme.Focused.SelectSelector = theme.Focused.SelectSelector.SetString("● ")
	form := huh.NewForm(huh.NewGroup(
		huh.NewSelect[string]().
			Title("Choose a Titan tool").
			Description("Use arrow keys to move and Enter to select.").
			Options(mainMenuOptions()...).
			Value(&selected),
	)).WithInput(stdin).WithOutput(stdout).WithTheme(theme)
	return selected, form.Run()
}

// mainMenuOptions lists the top level menu entries in display order.
func mainMenuOptions() []huh.Option[string] {
	return []huh.Option[string]{
		huh.NewOption("Monitor", "monitor"),
		huh.NewOption("Performance test", "perf-test"),
		huh.NewOption("Micro benchmark", "micro-bench"),
		huh.NewOption("Management", "management"),
	}
}

func selectPerfSettings(stdin io.Reader, stdout io.Writer) ([]string, error) {
	host := "127.0.0.1"
	port := "7777"
	destination := "/queue/perf-test"
	warmupMessages := "1000"
	messages := "10000"
	producers := "1"
	payloadBytes := "1024"
	connectTimeout := "5s"
	completionTimeout := "30s"

	form := huh.NewForm(huh.NewGroup(
		huh.NewInput().Title("Host").Value(&host).Validate(notBlank("host")),
		huh.NewInput().Title("Port").Value(&port).Validate(positiveNumber("port", false)),
		huh.NewInput().Title("Destination").Value(&destination).Validate(notBlank("destination")),
		huh.NewInput().Title("Warm-up messages").Value(&warmupMessages).Validate(positiveNumber("warm-up messages", true)),
		huh.NewInput().Title("Messages").Value(&messages).Validate(positiveNumber("messages", false)),
		huh.NewInput().Title("Producer connections").Value(&producers).Validate(positiveNumber("producers", false)),
		huh.NewInput().Title("Payload bytes").Value(&payloadBytes).Validate(minimumNumber("payload bytes", 20)),
		huh.NewInput().Title("Connect timeout").Value(&connectTimeout).Validate(durationValue),
		huh.NewInput().Title("Completion timeout").Value(&completionTimeout).Validate(durationValue),
	)).WithInput(stdin).WithOutput(stdout).WithTheme(huh.ThemeCharm())
	if err := form.Run(); err != nil {
		return nil, err
	}
	return perfSettingsArguments(
		host,
		port,
		destination,
		warmupMessages,
		messages,
		producers,
		payloadBytes,
		connectTimeout,
		completionTimeout,
	), nil
}

func perfSettingsArguments(
	host string,
	port string,
	destination string,
	warmupMessages string,
	messages string,
	producers string,
	payloadBytes string,
	connectTimeout string,
	completionTimeout string,
) []string {
	return []string{
		"perf-test",
		"--host", host,
		"--port", port,
		"--destination", destination,
		"--warmup-messages", warmupMessages,
		"--messages", messages,
		"--producers", producers,
		"--payload-bytes", payloadBytes,
		"--connect-timeout", connectTimeout,
		"--completion-timeout", completionTimeout,
	}
}

func notBlank(name string) func(string) error {
	return func(value string) error {
		if strings.TrimSpace(value) == "" {
			return fmt.Errorf("%s must not be blank", name)
		}
		return nil
	}
}

func positiveNumber(name string, allowZero bool) func(string) error {
	return func(value string) error {
		number, err := strconv.Atoi(value)
		if err != nil || number < 0 || (!allowZero && number == 0) {
			if allowZero {
				return fmt.Errorf("%s must be zero or greater", name)
			}
			return fmt.Errorf("%s must be greater than zero", name)
		}
		return nil
	}
}

func durationValue(value string) error {
	duration, err := time.ParseDuration(value)
	if err != nil || duration <= 0 {
		return fmt.Errorf("duration must be greater than zero, for example 5s")
	}
	return nil
}

func minimumNumber(name string, minimum int) func(string) error {
	return func(value string) error {
		number, err := strconv.Atoi(value)
		if err != nil || number < minimum {
			return fmt.Errorf("%s must be at least %d", name, minimum)
		}
		return nil
	}
}

func queueCommand(stdout io.Writer, rootOptions *viewOptions) *cobra.Command {
	options := &queueOptions{}
	command := &cobra.Command{
		Use:           "queue",
		Short:         "Manage dispatcher queues",
		SilenceUsage:  true,
		SilenceErrors: true,
		PersistentPreRun: func(cmd *cobra.Command, args []string) {
			options.addr = rootOptions.addr
			options.token = token(rootOptions.token)
			options.timeout = rootOptions.timeout
			options.noColor = rootOptions.noColor
		},
	}
	command.AddCommand(queueListCommand(stdout, options))
	command.AddCommand(queueCreateCommand(stdout, options))
	command.AddCommand(queueDeleteCommand(stdout, options))
	command.AddCommand(queueActionCommand(stdout, options, "pause", "Pause a dispatcher queue"))
	command.AddCommand(queueActionCommand(stdout, options, "resume", "Resume a dispatcher queue"))
	command.AddCommand(queueActionCommand(stdout, options, "purge", "Remove pending messages from a dispatcher queue"))
	return command
}

// queueActionCommand builds the pause, resume and purge subcommands, which
// differ only by the monitor client call they make.
func queueActionCommand(stdout io.Writer, options *queueOptions, action string, short string) *cobra.Command {
	return &cobra.Command{
		Use:           action + " <destination>",
		Short:         short,
		Args:          cobra.ExactArgs(1),
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			if err := applyQueueAction(cmd.Context(), client, action, args[0]); err != nil {
				return queueError(err)
			}
			fmt.Fprintf(stdout, "%sd %s\n", action, args[0])
			return nil
		},
	}
}

// applyQueueAction routes an action name to the matching monitor client call so
// the interactive menu and the cobra commands share one code path.
func applyQueueAction(ctx context.Context, client monitor.Client, action string, destination string) error {
	switch action {
	case "pause":
		return client.PauseQueue(ctx, destination)
	case "resume":
		return client.ResumeQueue(ctx, destination)
	case "purge":
		return client.PurgeQueue(ctx, destination)
	default:
		return fmt.Errorf("unsupported queue action %q", action)
	}
}

func queueListCommand(stdout io.Writer, options *queueOptions) *cobra.Command {
	return &cobra.Command{
		Use:           "list",
		Short:         "List dispatcher queues",
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			queues, err := client.Queues(cmd.Context())
			if err != nil {
				return queueError(err)
			}
			render.Queues(stdout, queues, render.Options{Color: !options.noColor})
			return nil
		},
	}
}

func queueCreateCommand(stdout io.Writer, options *queueOptions) *cobra.Command {
	command := &cobra.Command{
		Use:           "create <destination>",
		Short:         "Create a dispatcher queue",
		Args:          cobra.ExactArgs(1),
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if options.maxPendingBytes <= 0 {
				return exitError{code: 2, err: fmt.Errorf("max pending bytes must be greater than 0")}
			}
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			queue, err := client.CreateQueue(cmd.Context(), args[0], options.maxPendingBytes)
			if err != nil {
				return queueError(err)
			}
			fmt.Fprintf(stdout, "created %s size=%d maxPendingBytes=%d\n", queue.Destination, queue.Size, queue.MaxPendingBytes)
			return nil
		},
	}
	command.Flags().Int64Var(&options.maxPendingBytes, "max-pending-bytes", defaultMaxPendingBytes, "Maximum queued payload bytes")
	return command
}

func queueDeleteCommand(stdout io.Writer, options *queueOptions) *cobra.Command {
	command := &cobra.Command{
		Use:           "delete <destination>",
		Short:         "Delete a dispatcher queue",
		Args:          cobra.ExactArgs(1),
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			err := client.DeleteQueue(cmd.Context(), args[0], options.force)
			if err != nil {
				return queueError(err)
			}
			fmt.Fprintf(stdout, "deleted %s\n", args[0])
			return nil
		},
	}
	command.Flags().BoolVar(&options.force, "force", false, "Drop queued messages before deleting")
	return command
}

func versionCommand(stdout io.Writer, version string) *cobra.Command {
	return &cobra.Command{
		Use:           "version",
		Short:         "Print CLI version",
		SilenceUsage:  true,
		SilenceErrors: true,
		Run: func(cmd *cobra.Command, args []string) {
			fmt.Fprintln(stdout, version)
		},
	}
}

func runView(ctx context.Context, stdout io.Writer, options *viewOptions) error {
	if err := validate(options); err != nil {
		return exitError{code: 2, err: err}
	}

	client := monitor.NewClientWithTimeout(options.addr, token(options.token), options.timeout)
	for {
		snapshot, err := client.Snapshot(ctx)
		if !options.noClear {
			clear(stdout)
		}
		if err != nil {
			render.Error(stdout, options.addr, err, renderOptions(options))
			if options.once {
				return exitError{code: 1, err: err}
			}
		} else {
			render.Dashboard(stdout, snapshot, render.View(options.view), options.addr, renderOptions(options))
			if options.once {
				return nil
			}
		}

		select {
		case <-ctx.Done():
			return nil
		case <-time.After(options.interval):
		}
	}
}

func token(value string) string {
	if value != "" {
		return value
	}
	return os.Getenv("TITAN_MONITOR_TOKEN")
}

func queueError(err error) error {
	var httpErr monitor.HTTPError
	if errors.As(err, &httpErr) && httpErr.StatusCode == 409 {
		return exitError{code: 1, err: fmt.Errorf("%w; retry with --force to drop queued messages", err)}
	}
	return exitError{code: 1, err: err}
}

func renderOptions(options *viewOptions) render.Options {
	return render.Options{Color: !options.noColor}
}

func validate(options *viewOptions) error {
	if options.interval <= 0 {
		return fmt.Errorf("interval must be greater than 0")
	}
	if options.timeout <= 0 {
		return fmt.Errorf("timeout must be greater than 0")
	}
	if !render.ValidView(render.View(options.view)) {
		return fmt.Errorf("unsupported view %q", options.view)
	}
	return nil
}

func clear(w io.Writer) {
	fmt.Fprint(w, "\033[H\033[2J")
}
