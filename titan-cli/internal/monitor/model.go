package monitor

type Snapshot struct {
	Server ServerSnapshot  `json:"server"`
	JVM    JVMSnapshot     `json:"jvm"`
	Queues []QueueSnapshot `json:"queues"`
}

type ServerSnapshot struct {
	Version      string `json:"version"`
	StartedAt    any    `json:"startedAt"`
	Timestamp    any    `json:"timestamp"`
	UptimeMillis int64  `json:"uptimeMillis"`
}

type JVMSnapshot struct {
	CPU    CPUSnapshot    `json:"cpu"`
	Heap   HeapSnapshot   `json:"heap"`
	Thread ThreadSnapshot `json:"thread"`
}

type CPUSnapshot struct {
	SystemCPULoad       float64 `json:"systemCpuLoad"`
	ProcessCPULoad      float64 `json:"processCpuLoad"`
	AvailableProcessors int64   `json:"availableProcessors"`
}

type HeapSnapshot struct {
	Init      int64 `json:"init"`
	Used      int64 `json:"used"`
	Committed int64 `json:"committed"`
	Max       int64 `json:"max"`
}

type ThreadSnapshot struct {
	ThreadCount             int   `json:"threadCount"`
	PeakThreadCount         int   `json:"peakThreadCount"`
	TotalStartedThreadCount int64 `json:"totalStartedThreadCount"`
}

type QueueSnapshot struct {
	Destination string `json:"destination"`
	Size        int    `json:"size"`
	Capacity    int    `json:"capacity"`
	Paused      bool   `json:"paused"`
}
