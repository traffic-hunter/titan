package org.traffichunter.titan.monitor.model;

import java.time.Instant;

public record ServerSnapshot(String version, Instant startedAt, Instant timestamp, long uptimeMillis) {
}
