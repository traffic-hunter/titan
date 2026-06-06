package org.traffichunter.titan.monitor.model;

public record QueueSnapshot(String destination, int size, int capacity, boolean paused) {
}
