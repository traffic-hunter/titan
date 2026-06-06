package cli

import (
	"context"
	"flag"
	"fmt"
	"io"
	"time"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
	"github.com/traffic-hunter/titan/titan-cli/internal/render"
)

const defaultAddr = "http://localhost:7777"

func Run(args []string, stdout io.Writer, stderr io.Writer, version string) int {
	if len(args) == 0 {
		usage(stderr)
		return 2
	}
	if args[0] == "version" {
		fmt.Fprintln(stdout, version)
		return 0
	}
	if args[0] != "monitor" {
		fmt.Fprintf(stderr, "unknown command %q\n", args[0])
		usage(stderr)
		return 2
	}
	return runMonitor(args[1:], stdout, stderr)
}

func runMonitor(args []string, stdout io.Writer, stderr io.Writer) int {
	if len(args) == 0 {
		monitorUsage(stderr)
		return 2
	}

	switch args[0] {
	case "status":
		return snapshotCommand(args[1:], stdout, stderr, render.Status)
	case "queues":
		return snapshotCommand(args[1:], stdout, stderr, func(w io.Writer, s monitor.Snapshot) {
			render.Queues(w, s.Queues)
		})
	case "jvm":
		return snapshotCommand(args[1:], stdout, stderr, render.JVM)
	case "watch":
		return watchCommand(args[1:], stdout, stderr)
	default:
		fmt.Fprintf(stderr, "unknown monitor command %q\n", args[0])
		monitorUsage(stderr)
		return 2
	}
}

func snapshotCommand(args []string, stdout io.Writer, stderr io.Writer, renderer func(io.Writer, monitor.Snapshot)) int {
	flags := flag.NewFlagSet("snapshot", flag.ContinueOnError)
	flags.SetOutput(stderr)
	addr := flags.String("addr", defaultAddr, "Titan monitor HTTP address")
	token := flags.String("token", "", "Titan monitor bearer token")
	if err := flags.Parse(args); err != nil {
		return 2
	}

	snapshot, err := monitor.NewClient(*addr, *token).Snapshot(context.Background())
	if err != nil {
		fmt.Fprintf(stderr, "failed to fetch monitor snapshot: %v\n", err)
		return 1
	}
	renderer(stdout, snapshot)
	return 0
}

func watchCommand(args []string, stdout io.Writer, stderr io.Writer) int {
	flags := flag.NewFlagSet("watch", flag.ContinueOnError)
	flags.SetOutput(stderr)
	addr := flags.String("addr", defaultAddr, "Titan monitor HTTP address")
	token := flags.String("token", "", "Titan monitor bearer token")
	interval := flags.Duration("interval", time.Second, "Refresh interval")
	once := flags.Bool("once", false, "Render one frame and exit")
	if err := flags.Parse(args); err != nil {
		return 2
	}

	client := monitor.NewClient(*addr, *token)
	for {
		snapshot, err := client.Snapshot(context.Background())
		if err != nil {
			fmt.Fprintf(stderr, "failed to fetch monitor snapshot: %v\n", err)
			return 1
		}
		fmt.Fprint(stdout, "\033[H\033[2J")
		render.Status(stdout, snapshot)
		fmt.Fprintln(stdout)
		render.Queues(stdout, snapshot.Queues)
		if *once {
			return 0
		}
		time.Sleep(*interval)
	}
}

func usage(w io.Writer) {
	fmt.Fprintln(w, "usage: titan <version|monitor>")
	fmt.Fprintln(w, "usage: titan monitor <status|watch|queues|jvm> [flags]")
}

func monitorUsage(w io.Writer) {
	fmt.Fprintln(w, "usage: titan monitor <status|watch|queues|jvm> [--addr http://localhost:7777] [--token token]")
}
