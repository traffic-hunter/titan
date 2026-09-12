package render

import (
	"fmt"
	"io"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
)

const width = 86

const (
	reset  = "\033[0m"
	bold   = "\033[1m"
	muted  = "\033[2m"
	red    = "\033[31m"
	green  = "\033[32m"
	yellow = "\033[33m"
	cyan   = "\033[36m"
)

type Options struct {
	Color bool
	// Group limits the queue table to one destination group. Empty shows every group.
	Group string
}

var NoColor = Options{}

const cliBanner = `████████╗ ██╗ ████████╗  █████╗  ███╗   ██╗      ██████╗ ██╗      ██╗
╚══██╔══╝ ██║ ╚══██╔══╝ ██╔══██╗ ████╗  ██║     ██╔════╝ ██║      ██║
   ██║    ██║    ██║    ███████║ ██╔██╗ ██║     ██║      ██║      ██║
   ██║    ██║    ██║    ██╔══██║ ██║╚██╗██║     ██║      ██║      ██║
   ██║    ██║    ██║    ██║  ██║ ██║ ╚████║     ╚██████╗ ███████╗ ██║
   ╚═╝    ╚═╝    ╚═╝    ╚═╝  ╚═╝ ╚═╝  ╚═══╝      ╚═════╝ ╚══════╝ ╚═╝`

func Banner(w io.Writer, version string, options Options) {
	fmt.Fprintln(w, paint(cliBanner, cyan+bold, options))
	fmt.Fprintf(w, "%s\n\n", paint(fmt.Sprintf(":: Titan CLI %s ::", version), muted, options))
}

type View string

const (
	ViewOverview View = "overview"
	ViewQueues   View = "queues"
	ViewJVM      View = "jvm"
)

func ValidView(view View) bool {
	return view == ViewOverview || view == ViewQueues || view == ViewJVM
}

func Dashboard(w io.Writer, snapshot monitor.Snapshot, view View, addr string, options Options) {
	header(w, "Titan Monitor", addr, options)
	meta(w, snapshot, view, options)

	switch view {
	case ViewQueues:
		section(w, "Queues", options)
		Queues(w, snapshot.Queues, options)
	case ViewJVM:
		section(w, "JVM", options)
		JVM(w, snapshot, options)
	default:
		section(w, "Overview", options)
		Status(w, snapshot, options)
		fmt.Fprintln(w)
		section(w, "Queues", options)
		Queues(w, snapshot.Queues, options)
	}
	footer(w, options)
}

func Error(w io.Writer, addr string, err error, options Options) {
	header(w, "Titan Monitor", addr, options)
	section(w, "Connection", options)
	fmt.Fprintf(w, "  status  : %s\n", paint("disconnected", red, options))
	fmt.Fprintf(w, "  reason  : %v\n", err)
	fmt.Fprintln(w)
	fmt.Fprintf(w, "  %s\n", paint("Waiting for the next polling tick...", muted, options))
	footer(w, options)
}

func Status(w io.Writer, snapshot monitor.Snapshot, options Options) {
	fmt.Fprintf(w, "  %s %s\n", label("version", 11, options), valueOrUnknown(snapshot.Server.Version))
	fmt.Fprintf(w, "  %s %s\n", label("uptime", 11, options), uptime(snapshot.Server.UptimeMillis))
	fmt.Fprintf(w, "  %s %s system   %s process\n",
		label("cpu", 11, options),
		metricBar(snapshot.JVM.CPU.SystemCPULoad, 24, options),
		percent(snapshot.JVM.CPU.ProcessCPULoad),
	)
	fmt.Fprintf(w, "  %s %s %s / %s\n",
		label("heap", 11, options),
		metricBar(ratio(snapshot.JVM.Heap.Used, snapshot.JVM.Heap.Max), 24, options),
		byteSize(snapshot.JVM.Heap.Used),
		byteSize(snapshot.JVM.Heap.Max),
	)
	fmt.Fprintf(w, "  %s %d current   %d peak   %d total started\n",
		label("threads", 11, options),
		snapshot.JVM.Thread.ThreadCount,
		snapshot.JVM.Thread.PeakThreadCount,
		snapshot.JVM.Thread.TotalStartedThreadCount,
	)
	fmt.Fprintf(w, "  %s %s pending   %d active   %d non-writable\n",
		label("channel I/O", 11, options),
		byteSize(snapshot.ChannelWrites.PendingBytes),
		snapshot.ChannelWrites.ActiveBuffers,
		snapshot.ChannelWrites.NonWritableBuffers,
	)
	fmt.Fprintf(w, "  %s %d messages\n",
		label("slow skips", 11, options),
		snapshot.ChannelWrites.SkippedMessages,
	)
	queues := selectGroup(snapshot.Queues, options.Group)
	if options.Group == "" {
		fmt.Fprintf(w, "  %s %s in %s\n",
			label("queues", 11, options),
			count(len(queues), "queue"),
			count(groupCount(queues), "group"),
		)
		return
	}
	fmt.Fprintf(w, "  %s %s in group %s   %s server-wide\n",
		label("queues", 11, options),
		count(len(queues), "queue"),
		options.Group,
		count(len(snapshot.Queues), "queue"),
	)
}

func JVM(w io.Writer, snapshot monitor.Snapshot, options Options) {
	fmt.Fprintf(w, "  %s %s\n", label("cpu system", 12, options), metricBar(snapshot.JVM.CPU.SystemCPULoad, 32, options))
	fmt.Fprintf(w, "  %s %s\n", label("cpu process", 12, options), metricBar(snapshot.JVM.CPU.ProcessCPULoad, 32, options))
	fmt.Fprintf(w, "  %s %d\n", label("processors", 12, options), snapshot.JVM.CPU.AvailableProcessors)
	fmt.Fprintf(w, "  %s %s %s / %s\n",
		label("heap used", 12, options),
		metricBar(ratio(snapshot.JVM.Heap.Used, snapshot.JVM.Heap.Max), 32, options),
		byteSize(snapshot.JVM.Heap.Used),
		byteSize(snapshot.JVM.Heap.Max),
	)
	fmt.Fprintf(w, "  %s %s\n", label("heap commit", 12, options), byteSize(snapshot.JVM.Heap.Committed))
	fmt.Fprintf(w, "  %s %d current, %d peak, %d total started\n",
		label("threads", 12, options),
		snapshot.JVM.Thread.ThreadCount,
		snapshot.JVM.Thread.PeakThreadCount,
		snapshot.JVM.Thread.TotalStartedThreadCount,
	)
}

// queueRow lays out one queue line and the header above it. A queue is
// identified by its group and destination together, so both get a column.
const queueRow = "%-14s %-28s %7s  %9s  %9s  %-7s %s"

func Queues(w io.Writer, queues []monitor.QueueSnapshot, options Options) {
	copied := selectGroup(queues, options.Group)
	sort.Slice(copied, func(i, j int) bool {
		if copied[i].Group != copied[j].Group {
			return copied[i].Group < copied[j].Group
		}
		return copied[i].Destination < copied[j].Destination
	})

	if options.Group != "" {
		fmt.Fprintf(w, "  %s\n", paint("group filter: "+options.Group, muted, options))
	}
	if len(copied) == 0 {
		if options.Group != "" {
			fmt.Fprintf(w, "  %s\n", paint("No dispatcher queues in group "+options.Group+".", muted, options))
			return
		}
		fmt.Fprintf(w, "  %s\n", paint("No dispatcher queues registered.", muted, options))
		return
	}

	header := fmt.Sprintf(queueRow, "GROUP", "DESTINATION", "SIZE", "PENDING", "LIMIT", "STATE", "PRESSURE")
	fmt.Fprintf(w, "  %s\n", paint(header, cyan, options))
	fmt.Fprintf(w, "  %s\n", paint(strings.Repeat("-", len(header)), muted, options))
	for _, queue := range copied {
		state := "run"
		if queue.Paused {
			state = "pause"
		}
		fmt.Fprintf(w, "  %-14s %-28s %7s  %9s  %9s  %s %s\n",
			truncate(queue.Group, 14),
			truncate(queue.Destination, 28),
			compactInt(queue.Size),
			byteSize(queue.PendingBytes),
			byteSize(queue.MaxPendingBytes),
			queueState(state, options),
			metricBar(ratio(queue.PendingBytes, queue.MaxPendingBytes), 18, options),
		)
	}
}

// selectGroup keeps the queues of one group, or every queue when no group is set.
func selectGroup(queues []monitor.QueueSnapshot, group string) []monitor.QueueSnapshot {
	selected := make([]monitor.QueueSnapshot, 0, len(queues))
	for _, queue := range queues {
		if group == "" || queue.Group == group {
			selected = append(selected, queue)
		}
	}
	return selected
}

func groupCount(queues []monitor.QueueSnapshot) int {
	seen := make(map[string]struct{}, len(queues))
	for _, queue := range queues {
		seen[queue.Group] = struct{}{}
	}
	return len(seen)
}

func count(value int, noun string) string {
	if value == 1 {
		return fmt.Sprintf("%d %s", value, noun)
	}
	return fmt.Sprintf("%d %ss", value, noun)
}

func header(w io.Writer, title string, addr string, options Options) {
	rule := paint(strings.Repeat("=", width), cyan, options)
	fmt.Fprintln(w, rule)
	fmt.Fprintf(w, "%s %s\n",
		paint(fmt.Sprintf("%-42s", title), bold, options),
		paint(fmt.Sprintf("%43s", truncateLeft(addr, 43)), muted, options),
	)
	fmt.Fprintln(w, rule)
}

func meta(w io.Writer, snapshot monitor.Snapshot, view View, options Options) {
	fmt.Fprintf(w, "%s=%s  %s=%s  %s=%s  %s=%d\n",
		paint("view", muted, options),
		paint(string(view), green, options),
		paint("updated", muted, options),
		time.Now().Format("15:04:05"),
		paint("uptime", muted, options),
		uptime(snapshot.Server.UptimeMillis),
		paint("queues", muted, options),
		len(selectGroup(snapshot.Queues, options.Group)),
	)
}

func section(w io.Writer, title string, options Options) {
	fmt.Fprintln(w)
	text := fmt.Sprintf("-- %s %s", title, strings.Repeat("-", max(0, width-len(title)-4)))
	fmt.Fprintln(w, paint(text, cyan, options))
}

func footer(w io.Writer, options Options) {
	fmt.Fprintln(w)
	fmt.Fprintln(w, paint("Ctrl+C to quit. Use --view overview|queues|jvm.", muted, options))
}

func metricBar(value float64, barWidth int, options Options) string {
	return fmt.Sprintf("%s %s", bar(value, barWidth, options), percent(value))
}

func uptime(milliseconds int64) string {
	duration := (time.Duration(milliseconds) * time.Millisecond).Round(time.Second)
	if duration < time.Minute {
		return duration.String()
	}
	hours := int(duration / time.Hour)
	duration -= time.Duration(hours) * time.Hour
	minutes := int(duration / time.Minute)
	duration -= time.Duration(minutes) * time.Minute
	seconds := int(duration / time.Second)
	if hours > 0 {
		return fmt.Sprintf("%dh%02dm%02ds", hours, minutes, seconds)
	}
	return fmt.Sprintf("%dm%02ds", minutes, seconds)
}

func compactInt(value int) string {
	if value < 1000 {
		return strconv.Itoa(value)
	}
	units := []string{"", "K", "M", "B"}
	current := float64(value)
	unit := 0
	for current >= 1000 && unit < len(units)-1 {
		current /= 1000
		unit++
	}
	return fmt.Sprintf("%.1f%s", current, units[unit])
}

func max(a int, b int) int {
	if a > b {
		return a
	}
	return b
}

func truncateLeft(value string, length int) string {
	if len(value) <= length {
		return value
	}
	if length <= 1 {
		return value[len(value)-length:]
	}
	return "~" + value[len(value)-length+1:]
}

func percent(value float64) string {
	if value < 0 {
		return "unknown"
	}
	return fmt.Sprintf("%5.1f%%", value*100)
}

func ratio(used int64, max int64) float64 {
	if max <= 0 {
		return 0
	}
	return float64(used) / float64(max)
}

func bar(value float64, width int, options Options) string {
	if value < 0 {
		value = 0
	}
	if value > 1 {
		value = 1
	}
	filled := int(value * float64(width))
	fill := strings.Repeat("#", filled)
	empty := strings.Repeat(".", width-filled)
	if options.Color {
		fill = paint(fill, healthColor(value), options)
		empty = paint(empty, muted, options)
	}
	return "[" + fill + empty + "]"
}

func byteSize(value int64) string {
	if value < 0 {
		return "unknown"
	}
	const unit = 1024
	if value < unit {
		return fmt.Sprintf("%dB", value)
	}
	div, exp := int64(unit), 0
	for n := value / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f%ciB", float64(value)/float64(div), "KMGTPE"[exp])
}

func truncate(value string, length int) string {
	if len(value) <= length {
		return value
	}
	if length <= 1 {
		return value[:length]
	}
	return value[:length-1] + "~"
}

func valueOrUnknown(value string) string {
	if value == "" {
		return "unknown"
	}
	return value
}

func label(value string, width int, options Options) string {
	return paint(fmt.Sprintf("%-*s", width, value), cyan, options)
}

func queueState(value string, options Options) string {
	color := green
	if value == "pause" {
		color = yellow
	}
	return paint(fmt.Sprintf("%-7s", value), color, options)
}

func healthColor(value float64) string {
	switch {
	case value >= 0.85:
		return red
	case value >= 0.65:
		return yellow
	default:
		return green
	}
}

func paint(value string, color string, options Options) string {
	if !options.Color || value == "" {
		return value
	}
	return color + value + reset
}
