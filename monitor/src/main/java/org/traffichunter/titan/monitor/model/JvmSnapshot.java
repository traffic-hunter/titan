package org.traffichunter.titan.monitor.model;

import org.traffichunter.titan.monitor.jmx.cpu.CpuData;
import org.traffichunter.titan.monitor.jmx.heap.HeapData;
import org.traffichunter.titan.monitor.jmx.thread.ThreadData;

public record JvmSnapshot(CpuData cpu, HeapData heap, ThreadData thread) {
}
