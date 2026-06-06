package cli

import (
	"context"
	"errors"
	"fmt"
	"io"
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
	command.Flags().StringVar(&options.addr, "addr", defaultAddr, "Titan monitor HTTP address")
	command.Flags().StringVar(&options.token, "token", "", "Titan monitor bearer token")
	command.Flags().DurationVar(&options.interval, "interval", time.Second, "Polling interval")
	command.Flags().DurationVar(&options.timeout, "timeout", 5*time.Second, "HTTP request timeout")
	command.Flags().StringVar(&options.view, "view", "overview", "Initial view: overview, queues, or jvm")
	command.Flags().BoolVar(&options.noClear, "no-clear", false, "Render without clearing the terminal")
	command.Flags().BoolVar(&options.noColor, "no-color", false, "Render without ANSI colors")
	command.Flags().BoolVar(&options.once, "once", false, "Render one frame and exit")
	command.AddCommand(versionCommand(stdout, version))
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

	client := monitor.NewClientWithTimeout(options.addr, options.token, options.timeout)
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
