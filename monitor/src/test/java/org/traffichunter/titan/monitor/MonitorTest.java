package org.traffichunter.titan.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.bootstrap.monitor.SettingsMonitor;
import org.traffichunter.titan.monitor.jmx.JmxMbeanCollector;
import org.traffichunter.titan.monitor.jmx.heap.HeapData;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MonitorTest {

    @Test
    void check_type_safety() {
        Monitor monitor = new Monitor(SettingsMonitor.builder()
                .timeUnit(TimeUnit.SECONDS)
                .delay(10)
                .initialDelay(0)
                .build());

        JmxMbeanCollector<HeapData> collector = monitor.findMonitor(HeapData.class);

        HeapData collect = collector.collect();

        Assertions.assertNotNull(collect);
        Assertions.assertEquals(collect.getClass(), HeapData.class);
    }
}