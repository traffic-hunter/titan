package render

import (
	"fmt"
	"io"
	"sort"
	"strings"
	"time"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
)

func Status(w io.Writer, snapshot monitor.Snapshot) {
	fmt.Fprintf(w, "Titan %s\n", valueOrUnknown(snapshot.Server.Version))
	fmt.Fprintf(w, "Uptime: %s\n", (time.Duration(snapshot.Server.UptimeMillis) * time.Millisecond).Round(time.Second))
	fmt.Fprintf(w, "CPU   : %s system  %s process\n", percent(snapshot.JVM.CPU.SystemCPULoad), percent(snapshot.JVM.CPU.ProcessCPULoad))
	fmt.Fprintf(w, "Heap  : %s / %s %s\n", byteSize(snapshot.JVM.Heap.Used), byteSize(snapshot.JVM.Heap.Max), bar(ratio(snapshot.JVM.Heap.Used, snapshot.JVM.Heap.Max), 24))
	fmt.Fprintf(w, "Thread: %d current  %d peak\n", snapshot.JVM.Thread.ThreadCount, snapshot.JVM.Thread.PeakThreadCount)
	fmt.Fprintf(w, "Queues: %d destinations\n", len(snapshot.Queues))
}

func JVM(w io.Writer, snapshot monitor.Snapshot) {
	fmt.Fprintln(w, "JVM")
	fmt.Fprintf(w, "CPU system : %s %s\n", percent(snapshot.JVM.CPU.SystemCPULoad), bar(snapshot.JVM.CPU.SystemCPULoad, 24))
	fmt.Fprintf(w, "CPU process: %s %s\n", percent(snapshot.JVM.CPU.ProcessCPULoad), bar(snapshot.JVM.CPU.ProcessCPULoad, 24))
	fmt.Fprintf(w, "Processors : %d\n", snapshot.JVM.CPU.AvailableProcessors)
	fmt.Fprintf(w, "Heap used  : %s / %s %s\n", byteSize(snapshot.JVM.Heap.Used), byteSize(snapshot.JVM.Heap.Max), bar(ratio(snapshot.JVM.Heap.Used, snapshot.JVM.Heap.Max), 24))
	fmt.Fprintf(w, "Heap commit: %s\n", byteSize(snapshot.JVM.Heap.Committed))
	fmt.Fprintf(w, "Threads    : %d current, %d peak, %d total started\n",
		snapshot.JVM.Thread.ThreadCount,
		snapshot.JVM.Thread.PeakThreadCount,
		snapshot.JVM.Thread.TotalStartedThreadCount,
	)
}

func Queues(w io.Writer, queues []monitor.QueueSnapshot) {
	copied := append([]monitor.QueueSnapshot(nil), queues...)
	sort.Slice(copied, func(i, j int) bool {
		return copied[i].Destination < copied[j].Destination
	})

	fmt.Fprintln(w, "Queues")
	fmt.Fprintln(w, "DESTINATION                              SIZE   CAP    STATE   PRESSURE")
	for _, queue := range copied {
		state := "run"
		if queue.Paused {
			state = "pause"
		}
		fmt.Fprintf(w, "%-40s %5d %5d  %-6s %s\n",
			truncate(queue.Destination, 40),
			queue.Size,
			queue.Capacity,
			state,
			bar(ratio(int64(queue.Size), int64(queue.Capacity)), 18),
		)
	}
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

func bar(value float64, width int) string {
	if value < 0 {
		value = 0
	}
	if value > 1 {
		value = 1
	}
	filled := int(value * float64(width))
	return "[" + strings.Repeat("#", filled) + strings.Repeat(".", width-filled) + "]"
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
