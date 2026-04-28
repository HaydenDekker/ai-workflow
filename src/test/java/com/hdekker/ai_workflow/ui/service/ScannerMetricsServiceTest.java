package com.hdekker.ai_workflow.ui.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for {@link ScannerMetricsService}.
 * <p>
 * Verifies that the service correctly reads metrics from MeterRegistry.
 */
public class ScannerMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private ScannerMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new ScannerMetricsService(meterRegistry);
    }

    @Test
    void givenNoMetrics_WhenGetMetrics_ThenReturnsZeroValues() {
        var snapshot = metricsService.getMetrics("test-agent");

        assertThat(snapshot.agentId()).isEqualTo("test-agent");
        assertThat(snapshot.fileCount()).isZero();
        assertThat(snapshot.totalDiscovered()).isZero();
        assertThat(snapshot.unchanged()).isZero();
    }

    @Test
    void givenDiscoveredCounter_WhenGetMetrics_ThenReturnsCorrectValue() {
        meterRegistry.counter("ai_workflow.scanner.files_discovered",
                "agentId", "test-agent", "folder", "/test/path").increment(5);

        var snapshot = metricsService.getMetrics("test-agent");

        assertThat(snapshot.totalDiscovered()).isEqualTo(5);
    }

    @Test
    void givenUnchangedCounter_WhenGetMetrics_ThenReturnsCorrectValue() {
        meterRegistry.counter("ai_workflow.scanner.files_unchanged",
                "agentId", "test-agent", "folder", "/test/path").increment(10);

        var snapshot = metricsService.getMetrics("test-agent");

        assertThat(snapshot.unchanged()).isEqualTo(10);
    }

    @Test
    void givenFileCountGauge_WhenGetMetrics_ThenReturnsCorrectValue() {
        AtomicLong gaugeValue = new AtomicLong(7);
        Gauge.builder("ai_workflow.scanner.file_count", gaugeValue, AtomicLong::get)
                .tag("agentId", "test-agent")
                .register(meterRegistry);

        var snapshot = metricsService.getMetrics("test-agent");

        assertThat(snapshot.fileCount()).isEqualTo(7);
    }

    @Test
    void givenAllMetricsSet_WhenGetMetrics_ThenReturnsCompleteSnapshot() {
        // Set up all metrics
        meterRegistry.counter("ai_workflow.scanner.files_discovered",
                "agentId", "my-agent", "folder", "/data/inbox").increment(42);
        meterRegistry.counter("ai_workflow.scanner.files_unchanged",
                "agentId", "my-agent", "folder", "/data/inbox").increment(18);

        AtomicLong fileCount = new AtomicLong(24);
        Gauge.builder("ai_workflow.scanner.file_count", fileCount, AtomicLong::get)
                .tag("agentId", "my-agent")
                .register(meterRegistry);

        var snapshot = metricsService.getMetrics("my-agent");

        assertThat(snapshot.agentId()).isEqualTo("my-agent");
        assertThat(snapshot.fileCount()).isEqualTo(24);
        assertThat(snapshot.totalDiscovered()).isEqualTo(42);
        assertThat(snapshot.unchanged()).isEqualTo(18);
    }

    @Test
    void givenMultipleAgents_WhenGetMetrics_ThenReturnsCorrectValuesPerAgent() {
        // Set up metrics for agent-1
        meterRegistry.counter("ai_workflow.scanner.files_discovered",
                "agentId", "agent-1", "folder", "/data/agent1").increment(10);
        AtomicLong gauge1 = new AtomicLong(8);
        Gauge.builder("ai_workflow.scanner.file_count", gauge1, AtomicLong::get)
                .tag("agentId", "agent-1")
                .register(meterRegistry);

        // Set up metrics for agent-2
        meterRegistry.counter("ai_workflow.scanner.files_discovered",
                "agentId", "agent-2", "folder", "/data/agent2").increment(20);
        AtomicLong gauge2 = new AtomicLong(12);
        Gauge.builder("ai_workflow.scanner.file_count", gauge2, AtomicLong::get)
                .tag("agentId", "agent-2")
                .register(meterRegistry);

        var snapshot1 = metricsService.getMetrics("agent-1");
        var snapshot2 = metricsService.getMetrics("agent-2");

        assertThat(snapshot1.agentId()).isEqualTo("agent-1");
        assertThat(snapshot1.fileCount()).isEqualTo(8);
        assertThat(snapshot1.totalDiscovered()).isEqualTo(10);

        assertThat(snapshot2.agentId()).isEqualTo("agent-2");
        assertThat(snapshot2.fileCount()).isEqualTo(12);
        assertThat(snapshot2.totalDiscovered()).isEqualTo(20);
    }

    @Test
    void givenNoMetrics_WhenGetTotalFileCount_ThenReturnsZero() {
        assertThat(metricsService.getTotalFileCount()).isZero();
    }

    @Test
    void givenMultipleGauges_WhenGetTotalFileCount_ThenSumsAll() {
        AtomicLong g1 = new AtomicLong(3);
        Gauge.builder("ai_workflow.scanner.file_count", g1, AtomicLong::get)
                .register(meterRegistry);

        AtomicLong g2 = new AtomicLong(5);
        Gauge.builder("ai_workflow.scanner.file_count", g2, AtomicLong::get)
                .tag("agentId", "a")
                .register(meterRegistry);

        AtomicLong g3 = new AtomicLong(7);
        Gauge.builder("ai_workflow.scanner.file_count", g3, AtomicLong::get)
                .tag("agentId", "b")
                .register(meterRegistry);

        assertThat(metricsService.getTotalFileCount()).isEqualTo(15);
    }
}
