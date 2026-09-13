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
	group    string
	noClear  bool
	noColor  bool
	once     bool
}

type queueOptions struct {
	addr            string
	token           string
	timeout         time.Duration
	noColor         bool
	group           string
	maxPendingBytes int64
	force           bool
}

type perfOptions struct {
	host              string
	port              int
	transport         string
	webSocketPath     string
	sendMode          string
	pathLabel         string
	group             string
	destination       string
	warmupMessages    int
	messages          int
	producers         int
	payloadBytes      int
	connectTimeout    time.Duration
	completionTimeout time.Duration
	runnerPath        string
	resultsDir        string
	runID             string
	iteration         int
	fixtureManifest   string
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
	command.Flags().StringVar(&options.group, "group", "", "Show only queues in this destination group")
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
	command.Flags().StringVar(&options.group, "group", "", "Show only queues in this destination group")
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
				Transport:         options.transport,
				WebSocketPath:     options.webSocketPath,
				SendMode:          options.sendMode,
				PathLabel:         options.pathLabel,
				Group:             options.group,
				Destination:       options.destination,
				WarmupMessages:    options.warmupMessages,
				Messages:          options.messages,
				Producers:         options.producers,
				PayloadBytes:      options.payloadBytes,
				ConnectTimeout:    options.connectTimeout,
				CompletionTimeout: options.completionTimeout,
				RunnerPath:        options.runnerPath,
				ResultsDir:        options.resultsDir,
				RunID:             options.runID,
				Iteration:         options.iteration,
				FixtureManifest:   options.fixtureManifest,
			})
			if err != nil {
				return exitError{code: 1, err: err}
			}
			printPerfReport(stdout, report)
			if !report.Successful() {
				return exitError{code: 1, err: fmt.Errorf("performance test did not account for every message")}
			}
			return nil
		},
	}
	command.Flags().StringVar(&options.host, "host", "127.0.0.1", "Titan STOMP host")
	command.Flags().IntVar(&options.port, "port", 7777, "Titan STOMP port")
	command.Flags().StringVar(&options.transport, "transport", perf.TransportTCP, "Transport: tcp or websocket")
	command.Flags().StringVar(&options.webSocketPath, "websocket-path", "/stomp", "WebSocket upgrade path")
	command.Flags().StringVar(&options.sendMode, "send-mode", perf.SendModeReceipt,
		"receipt waits for the broker to accept each message; write only submits the local write")
	command.Flags().StringVar(&options.pathLabel, "path-label", perf.PathDispatch,
		"Server path the fixture is running: dispatch or direct")
	command.Flags().StringVar(&options.group, "group", "", "Destination group; empty uses the default group")
	command.Flags().StringVar(&options.destination, "destination", "/queue/perf-test", "STOMP destination")
	command.Flags().IntVar(&options.warmupMessages, "warmup-messages", 1_000, "Number of warm-up messages")
	command.Flags().IntVar(&options.messages, "messages", 10_000, "Number of messages")
	command.Flags().IntVar(&options.producers, "producers", 1, "Concurrent producer connections")
	command.Flags().IntVar(&options.payloadBytes, "payload-bytes", 1_024, "Payload size in bytes")
	command.Flags().DurationVar(&options.connectTimeout, "connect-timeout", 5*time.Second, "Connection timeout")
	command.Flags().DurationVar(&options.completionTimeout, "completion-timeout", 120*time.Second, "Overall test deadline")
	command.Flags().StringVar(&options.runnerPath, "runner", "", "Path to the Titan Java performance runner")
	command.Flags().StringVar(&options.resultsDir, "results-dir", "", "Directory to keep the raw result and run manifest in")
	command.Flags().StringVar(&options.runID, "run-id", "", "Name of this run inside the results directory")
	command.Flags().IntVar(&options.iteration, "iteration", 0, "Repetition number of this run")
	command.Flags().StringVar(&options.fixtureManifest, "fixture-manifest", "",
		"Manifest written by the stability fixture, copied into the run manifest")
	return command
}

func printPerfReport(output io.Writer, report perf.Report) {
	fmt.Fprintln(output, "Titan performance test")
	fmt.Fprintf(output, "  queue          : %s\n", queueRef(report.Group, report.Destination))
	fmt.Fprintf(output, "  run            : %s over %s on the %s path, %d producers\n",
		report.SendMode, report.Transport, report.PathLabel, report.Producers)
	fmt.Fprintf(output, "  requested      : %d\n", report.Requested)
	fmt.Fprintf(output, "  attempted      : %d (not attempted %d)\n", report.Attempted, report.NotAttempted)
	fmt.Fprintf(output, "  write submitted: %s\n", countOrUnsupported(report.WriteSubmitted))
	fmt.Fprintf(output, "  written        : %s\n", countOrUnsupported(report.Written))
	fmt.Fprintf(output, "  accepted       : %s\n", countOrUnsupported(report.Accepted))
	fmt.Fprintf(output, "  rejected       : %d\n", report.Rejected)
	fmt.Fprintf(output, "  not sent       : %d\n", report.LocalNotSent)
	fmt.Fprintf(output, "  unknown        : %d\n", report.Unknown)
	fmt.Fprintf(output, "  received       : %d (duplicates %d)\n", report.Received, report.Duplicates)
	fmt.Fprintf(output, "  missing        : %d\n", report.AcceptedNotReceived)
	fmt.Fprintf(output, "  contradictions : %d\n", report.Contradiction)
	if report.ForeignMessages > 0 || report.MalformedMessages > 0 {
		fmt.Fprintf(output, "  stray messages : %d foreign, %d malformed\n",
			report.ForeignMessages, report.MalformedMessages)
	}
	fmt.Fprintf(output, "  warm-up        : %d of %d\n", report.WarmupReceived, report.WarmupRequested)
	fmt.Fprintf(output, "  elapsed        : %.3f s\n", report.Elapsed.Seconds())
	fmt.Fprintf(output, "  throughput     : %.2f msg/s\n", report.Throughput)
	printLatency(output, "delivery", report.DeliveryLatency)
	printLatency(output, "receipt", report.ReceiptLatency)
	if !report.CountsBalanced {
		fmt.Fprintln(output, "  counts do not add up; the run is not a measurement")
	}
	if !report.CompletedBeforeDeadline {
		fmt.Fprintln(output, "  the deadline passed before the run settled")
	}
	if !report.ProducersStopped {
		fmt.Fprintln(output, "  producer threads were still running when the report was frozen")
	}
	for _, failure := range report.CleanupErrors {
		fmt.Fprintf(output, "  cleanup        : %s\n", failure)
	}
	if report.ResultsPath != "" {
		fmt.Fprintf(output, "  results        : %s\n", report.ResultsPath)
	}
}

// countOrUnsupported prints a stage this runner cannot observe as such, never as a zero.
func countOrUnsupported(count *int) string {
	if count == nil {
		return "not measured"
	}
	return strconv.Itoa(*count)
}

func printLatency(output io.Writer, name string, latency *perf.Latency) {
	if latency == nil {
		return
	}
	fmt.Fprintf(output, "  %-8s p50/p95/p99/max: %.3f / %.3f / %.3f / %.3f ms (%d samples)\n",
		name,
		milliseconds(latency.P50),
		milliseconds(latency.P95),
		milliseconds(latency.P99),
		milliseconds(latency.Max),
		latency.Samples,
	)
}

func milliseconds(duration time.Duration) float64 {
	return float64(duration.Microseconds()) / 1_000
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
	settings := perfSettings{
		host:              "127.0.0.1",
		port:              "7777",
		transport:         perf.TransportTCP,
		sendMode:          perf.SendModeReceipt,
		pathLabel:         perf.PathDispatch,
		destination:       "/queue/perf-test",
		warmupMessages:    "1000",
		messages:          "10000",
		producers:         "1",
		payloadBytes:      "1024",
		connectTimeout:    "5s",
		completionTimeout: "120s",
	}

	form := huh.NewForm(huh.NewGroup(
		huh.NewInput().Title("Host").Value(&settings.host).Validate(notBlank("host")),
		huh.NewInput().Title("Port").Value(&settings.port).Validate(positiveNumber("port", false)),
		huh.NewSelect[string]().Title("Transport").Options(
			huh.NewOption("TCP", perf.TransportTCP),
			huh.NewOption("WebSocket", perf.TransportWebSocket),
		).Value(&settings.transport),
		huh.NewSelect[string]().Title("Send mode").
			Description("Receipt waits for the broker to accept each message.").
			Options(
				huh.NewOption("Receipt", perf.SendModeReceipt),
				huh.NewOption("Write", perf.SendModeWrite),
			).Value(&settings.sendMode),
		huh.NewSelect[string]().Title("Server path").
			Description("Must match the path the server is running.").
			Options(
				huh.NewOption("Dispatch queues", perf.PathDispatch),
				huh.NewOption("Direct STOMP", perf.PathDirect),
			).Value(&settings.pathLabel),
		huh.NewInput().Title("Group").Description("Leave empty to use the default group.").Value(&settings.group).Validate(optionalGroup),
		huh.NewInput().Title("Destination").Value(&settings.destination).Validate(notBlank("destination")),
		huh.NewInput().Title("Warm-up messages").Value(&settings.warmupMessages).Validate(positiveNumber("warm-up messages", true)),
		huh.NewInput().Title("Messages").Value(&settings.messages).Validate(positiveNumber("messages", false)),
		huh.NewInput().Title("Producer connections").Value(&settings.producers).Validate(positiveNumber("producers", false)),
		huh.NewInput().Title("Payload bytes").Value(&settings.payloadBytes).Validate(minimumNumber("payload bytes", 24)),
		huh.NewInput().Title("Connect timeout").Value(&settings.connectTimeout).Validate(durationValue),
		huh.NewInput().Title("Completion timeout").Value(&settings.completionTimeout).Validate(durationValue),
	)).WithInput(stdin).WithOutput(stdout).WithTheme(huh.ThemeCharm())
	if err := form.Run(); err != nil {
		return nil, err
	}
	return perfSettingsArguments(settings), nil
}

// perfSettings holds one interactive answer per performance test option.
type perfSettings struct {
	host              string
	port              string
	transport         string
	sendMode          string
	pathLabel         string
	group             string
	destination       string
	warmupMessages    string
	messages          string
	producers         string
	payloadBytes      string
	connectTimeout    string
	completionTimeout string
}

func perfSettingsArguments(settings perfSettings) []string {
	return []string{
		"perf-test",
		"--host", settings.host,
		"--port", settings.port,
		"--transport", settings.transport,
		"--send-mode", settings.sendMode,
		"--path-label", settings.pathLabel,
		"--group", settings.group,
		"--destination", settings.destination,
		"--warmup-messages", settings.warmupMessages,
		"--messages", settings.messages,
		"--producers", settings.producers,
		"--payload-bytes", settings.payloadBytes,
		"--connect-timeout", settings.connectTimeout,
		"--completion-timeout", settings.completionTimeout,
	}
}

// optionalGroup accepts an empty value, which means the default group, and
// otherwise requires a name the server would accept.
func optionalGroup(value string) error {
	_, err := monitor.NormalizeGroup(value)
	return err
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
	command.PersistentFlags().StringVar(
		&options.group,
		"group",
		"",
		"Destination group; omit to list every group, or to target the default group on a change",
	)
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
			group, err := monitor.NormalizeGroup(options.group)
			if err != nil {
				return exitError{code: 2, err: err}
			}
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			if err := applyQueueAction(cmd.Context(), client, action, group, args[0]); err != nil {
				return queueError(err)
			}
			fmt.Fprintf(stdout, "%sd %s\n", action, queueRef(group, args[0]))
			return nil
		},
	}
}

// applyQueueAction routes an action name to the matching monitor client call so
// the interactive menu and the cobra commands share one code path.
func applyQueueAction(
	ctx context.Context,
	client monitor.Client,
	action string,
	group string,
	destination string,
) error {
	switch action {
	case "pause":
		return client.PauseQueue(ctx, group, destination)
	case "resume":
		return client.ResumeQueue(ctx, group, destination)
	case "purge":
		return client.PurgeQueue(ctx, group, destination)
	default:
		return fmt.Errorf("unsupported queue action %q", action)
	}
}

// queueRef names a queue the way the server does, by group and destination
// together. Printed in full even when the table had to truncate a column.
func queueRef(group string, destination string) string {
	return group + ":" + destination
}

// resolveListGroup reads the filter for a list request. An omitted flag lists
// every group; an explicitly blank one means the default group, matching how
// the server reads a blank parameter.
func resolveListGroup(cmd *cobra.Command, value string) (string, error) {
	if !cmd.Flags().Changed("group") {
		return "", nil
	}
	return monitor.NormalizeGroup(value)
}

func queueListCommand(stdout io.Writer, options *queueOptions) *cobra.Command {
	return &cobra.Command{
		Use:           "list",
		Short:         "List dispatcher queues",
		SilenceUsage:  true,
		SilenceErrors: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			group, err := resolveListGroup(cmd, options.group)
			if err != nil {
				return exitError{code: 2, err: err}
			}
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			queues, err := client.Queues(cmd.Context(), group)
			if err != nil {
				return queueError(err)
			}
			render.Queues(stdout, queues, render.Options{Color: !options.noColor, Group: group})
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
			group, err := monitor.NormalizeGroup(options.group)
			if err != nil {
				return exitError{code: 2, err: err}
			}
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			queue, err := client.CreateQueue(cmd.Context(), group, args[0], options.maxPendingBytes)
			if err != nil {
				return queueError(err)
			}
			fmt.Fprintf(
				stdout,
				"created %s size=%d maxPendingBytes=%d\n",
				queueRef(queue.Group, queue.Destination),
				queue.Size,
				queue.MaxPendingBytes,
			)
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
			group, err := monitor.NormalizeGroup(options.group)
			if err != nil {
				return exitError{code: 2, err: err}
			}
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			if err := client.DeleteQueue(cmd.Context(), group, args[0], options.force); err != nil {
				return queueError(err)
			}
			fmt.Fprintf(stdout, "deleted %s\n", queueRef(group, args[0]))
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
	return render.Options{Color: !options.noColor, Group: options.group}
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
	if options.group != "" && !monitor.ValidGroup(options.group) {
		return fmt.Errorf("invalid group name %q", options.group)
	}
	return nil
}

func clear(w io.Writer) {
	fmt.Fprint(w, "\033[H\033[2J")
}
