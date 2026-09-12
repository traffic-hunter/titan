package render

import (
	"bytes"
	"strings"
	"testing"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
)

func TestBannerIncludesCliNameAndVersion(t *testing.T) {
	var output bytes.Buffer

	Banner(&output, "0.9.0", NoColor)

	for _, expected := range []string{"████████╗", "Titan CLI 0.9.0"} {
		if !strings.Contains(output.String(), expected) {
			t.Fatalf("expected %q in banner:\n%s", expected, output.String())
		}
	}
}

func TestStatusRendersCoreFields(t *testing.T) {
	var out bytes.Buffer

	Status(&out, fixture(), NoColor)

	text := out.String()
	for _, expected := range []string{"version     0.6.1", "cpu", "heap", "channel I/O 2.0KiB pending   3 active   1 non-writable", "slow skips  2 messages", "queues      2 queues in 2 groups"} {
		if !strings.Contains(text, expected) {
			t.Fatalf("expected %q in output:\n%s", expected, text)
		}
	}
}

func TestQueuesRendersDestinationPressure(t *testing.T) {
	var out bytes.Buffer

	Queues(&out, fixture().Queues, NoColor)

	text := out.String()
	for _, expected := range []string{"GROUP", "/queue/orders", "market", "pause", "[#########.........]"} {
		if !strings.Contains(text, expected) {
			t.Fatalf("expected %q in output:\n%s", expected, text)
		}
	}
}

func TestQueuesSortByGroupThenDestination(t *testing.T) {
	var out bytes.Buffer

	Queues(&out, []monitor.QueueSnapshot{
		{Group: "market", Destination: "/queue/a"},
		{Group: "default", Destination: "/queue/b"},
		{Group: "default", Destination: "/queue/a"},
	}, NoColor)

	text := out.String()
	first := strings.Index(text, "default        /queue/a")
	second := strings.Index(text, "default        /queue/b")
	third := strings.Index(text, "market         /queue/a")
	if first < 0 || second < 0 || third < 0 {
		t.Fatalf("expected every queue row:\n%s", text)
	}
	if !(first < second && second < third) {
		t.Fatalf("expected group then destination order:\n%s", text)
	}
}

func TestQueuesKeepTheSameDestinationInDifferentGroupsApart(t *testing.T) {
	var out bytes.Buffer

	Queues(&out, fixture().Queues, NoColor)

	text := out.String()
	if strings.Count(text, "/queue/orders") != 2 {
		t.Fatalf("expected both queues for one destination:\n%s", text)
	}
}

func TestQueuesFilterToOneGroup(t *testing.T) {
	var out bytes.Buffer

	Queues(&out, fixture().Queues, Options{Group: "market"})

	text := out.String()
	if !strings.Contains(text, "group filter: market") {
		t.Fatalf("expected the filter to be named:\n%s", text)
	}
	if strings.Count(text, "/queue/orders") != 1 {
		t.Fatalf("expected only the market queue:\n%s", text)
	}
}

func TestQueuesReportAnEmptyGroup(t *testing.T) {
	var out bytes.Buffer

	Queues(&out, fixture().Queues, Options{Group: "unused"})

	if !strings.Contains(out.String(), "No dispatcher queues in group unused.") {
		t.Fatalf("expected an empty group message:\n%s", out.String())
	}
}

func TestStatusReportsTheFilteredScope(t *testing.T) {
	var out bytes.Buffer

	Status(&out, fixture(), Options{Group: "market"})

	if !strings.Contains(out.String(), "queues      1 queue in group market   2 queues server-wide") {
		t.Fatalf("expected the filtered scope:\n%s", out.String())
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
		ChannelWrites: monitor.ChannelWriteSnapshot{
			ActiveBuffers:      3,
			PendingBytes:       2048,
			NonWritableBuffers: 1,
			SkippedMessages:    2,
		},
		Queues: []monitor.QueueSnapshot{
			{Group: "market", Destination: "/queue/orders", Size: 5, PendingBytes: 20, MaxPendingBytes: 40, Paused: true},
			{Group: "default", Destination: "/queue/orders", Size: 1, PendingBytes: 4, MaxPendingBytes: 40},
		},
	}
}
