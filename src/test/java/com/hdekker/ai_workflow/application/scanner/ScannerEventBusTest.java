package com.hdekker.ai_workflow.application.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;

/**
 * Unit tests for {@link ScannerEventBus}.
 */
class ScannerEventBusTest {

    private ScannerEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new ScannerEventBus();
    }

    @Test
    void givenNoCallbacks_WhenPublish_ThenNoException() {
        eventBus.publish("agent-1", ScannerFileResult.EMITTED, "/tmp/test", null);
        eventBus.publish("agent-1", ScannerFileResult.ERROR, null, "oops");
    }

    @Test
    void givenCallbackRegistered_WhenPublish_ThenCallbackReceivesEvent() {
        CopyOnWriteArrayList<ScannerFileEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish("agent-1", ScannerFileResult.EMITTED, "/tmp/test", null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(0).result()).isEqualTo(ScannerFileResult.EMITTED);
        assertThat(events.get(0).folderPath()).isEqualTo("/tmp/test");
        assertThat(events.get(0).errorMessage()).isNull();
    }

    @Test
    void givenErrorEvent_WhenPublish_ThenCallbackReceivesErrorMessage() {
        CopyOnWriteArrayList<ScannerFileEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish("agent-1", ScannerFileResult.ERROR, null, "disk full");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).result()).isEqualTo(ScannerFileResult.ERROR);
        assertThat(events.get(0).errorMessage()).isEqualTo("disk full");
        assertThat(events.get(0).folderPath()).isNull();
    }

    @Test
    void givenFilteredEvent_WhenPublish_ThenCallbackReceivesFilteredResult() {
        CopyOnWriteArrayList<ScannerFileEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish("agent-1", ScannerFileResult.FILTERED, "/tmp/test", null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).result()).isEqualTo(ScannerFileResult.FILTERED);
        assertThat(events.get(0).folderPath()).isEqualTo("/tmp/test");
    }

    @Test
    void givenMultipleCallbacks_WhenPublish_ThenAllInvoked() {
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();

        eventBus.registerCallback(e -> count1.incrementAndGet());
        eventBus.registerCallback(e -> count2.incrementAndGet());

        eventBus.publish("agent-1", ScannerFileResult.EMITTED, "/tmp/test", null);

        assertThat(count1).hasValue(1);
        assertThat(count2).hasValue(1);
    }

    @Test
    void givenCallbackUnregistered_WhenPublish_ThenCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<ScannerFileEvent> callback = e -> count.incrementAndGet();
        eventBus.registerCallback(callback);
        eventBus.unregisterCallback(callback);

        eventBus.publish("agent-1", ScannerFileResult.EMITTED, "/tmp/test", null);

        assertThat(count).hasValue(0);
    }

    @Test
    void givenCallbackThrows_WhenPublish_ThenOtherCallbacksStillInvoked() {
        CopyOnWriteArrayList<ScannerFileEvent> goodEvents = new CopyOnWriteArrayList<>();

        eventBus.registerCallback(e -> {
            throw new RuntimeException("callback error");
        });
        eventBus.registerCallback(goodEvents::add);

        eventBus.publish("agent-1", ScannerFileResult.EMITTED, "/tmp/test", null);

        assertThat(goodEvents).hasSize(1);
    }

    @Test
    void givenMultiplePublishes_WhenCallbacksRegistered_ThenAllReceived() {
        CopyOnWriteArrayList<ScannerFileEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish("agent-1", ScannerFileResult.EMITTED, "/tmp/a", null);
        eventBus.publish("agent-2", ScannerFileResult.FILTERED, "/tmp/b", null);
        eventBus.publish("agent-1", ScannerFileResult.ERROR, null, "fail");

        assertThat(events).hasSize(3);
        assertThat(events.get(0).agentId()).isEqualTo("agent-1");
        assertThat(events.get(1).agentId()).isEqualTo("agent-2");
        assertThat(events.get(2).result()).isEqualTo(ScannerFileResult.ERROR);
    }

    @Test
    void givenCallbackRemoved_ThenOnlyRemainingInvoked() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<ScannerFileEvent> first = e -> count.incrementAndGet();
        java.util.function.Consumer<ScannerFileEvent> second = e -> count.incrementAndGet();

        eventBus.registerCallback(first);
        eventBus.registerCallback(second);
        eventBus.unregisterCallback(first);

        eventBus.publish("agent-1", ScannerFileResult.EMITTED, "/tmp/test", null);

        assertThat(count).hasValue(1); // only second was invoked
    }
}
