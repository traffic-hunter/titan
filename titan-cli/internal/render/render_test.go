package render

import (
	"bytes"
	"strings"
	"testing"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
)

func TestStatusRendersCoreFields(t *testing.T) {
	var out bytes.Buffer

	Status(&out, fixture(), NoColor)

	text := out.String()
	for _, expected := range []string{"version     0.6.1", "cpu", "heap", "queues      1 destinations"} {
		if !strings.Contains(text, expected) {
			t.Fatalf("expected %q in output:\n%s", expected, text)
		}
	}
}

func TestQueuesRendersDestinationPressure(t *testing.T) {
	var out bytes.Buffer

	Queues(&out, fixture().Queues, NoColor)

	text := out.String()
	for _, expected := range []string{"/queue/orders", "pause", "[#########.........]"} {
		if !strings.Contains(text, expected) {
			t.Fatalf("expected %q in output:\n%s", expected, text)
		}
	}
}

func fixture() monitor.Snapshot {
	return monitor.Snapshot{
		Server: monitor.ServerSnapshot{
			Version:      "0.6.1",
			UptimeMillis: 123000,
		},
		JVM: monitor.JVMSnapshot{
			CPU: monitor.CPUSnapshot{
				SystemCPULoad:       0.25,
				ProcessCPULoad:      0.10,
				AvailableProcessors: 8,
			},
			Heap: monitor.HeapSnapshot{
				Used: 512,
				Max:  1024,
			},
			Thread: monitor.ThreadSnapshot{
				ThreadCount:     4,
				PeakThreadCount: 8,
			},
		},
		Queues: []monitor.QueueSnapshot{
			{Destination: "/queue/orders", Size: 5, Capacity: 10, Paused: true},
		},
	}
}
