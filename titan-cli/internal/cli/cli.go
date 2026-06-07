package cli

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
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
	addr     string
	token    string
	timeout  time.Duration
	noColor  bool
	capacity int
	force    bool
}

func Run(args []string, stdout io.Writer, stderr io.Writer, version string) int {
	command := newRootCommand(stdout, stderr, version)
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

func newRootCommand(stdout io.Writer, stderr io.Writer, version string) *cobra.Command {
	options := &viewOptions{}
	command := &cobra.Command{
		Use:           "titan",
		Short:         "Titan terminal monitor",
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
	command.AddCommand(queueCommand(stdout, options))
	command.AddCommand(versionCommand(stdout, version))
	return command
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
	return command
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
			if options.capacity <= 0 {
				return exitError{code: 2, err: fmt.Errorf("capacity must be greater than 0")}
			}
			client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
			queue, err := client.CreateQueue(cmd.Context(), args[0], options.capacity)
			if err != nil {
				return queueError(err)
			}
			fmt.Fprintf(stdout, "created %s size=%d capacity=%d\n", queue.Destination, queue.Size, queue.Capacity)
			return nil
		},
	}
	command.Flags().IntVar(&options.capacity, "capacity", 11, "Queue capacity")
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
