package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEvent;
import com.hdekker.ai_workflow.domain.pipeline.AgentObserverEventType;

/**
 * Unit tests for {@link AgentObserverEventBus}.
 */
class AgentObserverEventBusTest {

    private AgentObserverEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new AgentObserverEventBus();
    }

    // -- publish with no callbacks --

    @Test
    void givenNoCallbacks_WhenPublishDispatchEvent_ThenNoException() {
        AgentObserverEvent event = AgentObserverEvent.dispatched("agent-1", "test.txt");
        eventBus.publish(event);
    }

    @Test
    void givenNoCallbacks_WhenPublishStoredEvent_ThenNoException() {
        AgentObserverEvent event = AgentObserverEvent.stored("agent-1", "output.txt");
        eventBus.publish(event);
    }

    // -- dispatch event --

    @Test
    void givenCallbackRegistered_WhenPublishDispatchEvent_ThenCallbackReceivesEvent() {
        CopyOnWriteArrayList<AgentObserverEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish(AgentObserverEvent.dispatched("agent-1", "test.txt"));

        assertThat(events).hasSize(1);
        AgentObserverEvent received = events.get(0);
        assertThat(received.agentId()).isEqualTo("agent-1");
        assertThat(received.eventType()).isEqualTo(AgentObserverEventType.DISPATCHED);
        assertThat(received.fileName()).isEqualTo("test.txt");
        assertThat(received.timestamp()).isNotNull();
    }

    // -- storage event --

    @Test
    void givenCallbackRegistered_WhenPublishStoredEvent_ThenCallbackReceivesEvent() {
        CopyOnWriteArrayList<AgentObserverEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish(AgentObserverEvent.stored("agent-1", "output.txt"));

        assertThat(events).hasSize(1);
        AgentObserverEvent received = events.get(0);
        assertThat(received.agentId()).isEqualTo("agent-1");
        assertThat(received.eventType()).isEqualTo(AgentObserverEventType.STORED);
        assertThat(received.fileName()).isEqualTo("output.txt");
        assertThat(received.timestamp()).isNotNull();
    }

    // -- multiple callbacks --

    @Test
    void givenMultipleCallbacks_WhenPublish_ThenAllInvoked() {
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();

        eventBus.registerCallback(e -> count1.incrementAndGet());
        eventBus.registerCallback(e -> count2.incrementAndGet());

        eventBus.publish(AgentObserverEvent.dispatched("agent-1", "test.txt"));

        assertThat(count1).hasValue(1);
        assertThat(count2).hasValue(1);
    }

    // -- callback unregistration --

    @Test
    void givenCallbackUnregistered_WhenPublish_ThenCallbackNotInvoked() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<AgentObserverEvent> callback = e -> count.incrementAndGet();
        eventBus.registerCallback(callback);
        eventBus.unregisterCallback(callback);

        eventBus.publish(AgentObserverEvent.dispatched("agent-1", "test.txt"));

        assertThat(count).hasValue(0);
    }

    // -- callback error isolation --

    @Test
    void givenCallbackThrows_WhenPublish_ThenOtherCallbacksStillInvoked() {
        CopyOnWriteArrayList<AgentObserverEvent> goodEvents = new CopyOnWriteArrayList<>();

        eventBus.registerCallback(e -> {
            throw new RuntimeException("callback error");
        });
        eventBus.registerCallback(goodEvents::add);

        eventBus.publish(AgentObserverEvent.dispatched("agent-1", "test.txt"));

        assertThat(goodEvents).hasSize(1);
    }

    // -- multiple publishes --

    @Test
    void givenMultiplePublishes_WhenCallbacksRegistered_ThenAllReceived() {
        CopyOnWriteArrayList<AgentObserverEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish(AgentObserverEvent.dispatched("agent-1", "file1.txt"));
        eventBus.publish(AgentObserverEvent.stored("agent-1", "output1.txt"));
        eventBus.publish(AgentObserverEvent.dispatched("agent-2", "file2.txt"));

        assertThat(events).hasSize(3);
        assertThat(events.get(0).eventType()).isEqualTo(AgentObserverEventType.DISPATCHED);
        assertThat(events.get(1).eventType()).isEqualTo(AgentObserverEventType.STORED);
        assertThat(events.get(2).agentId()).isEqualTo("agent-2");
    }

    // -- mixed agent dispatch and storage --

    @Test
    void givenMixedEvents_WhenPublished_ThenEventsPreserveAgentAndType() {
        CopyOnWriteArrayList<AgentObserverEvent> events = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(events::add);

        eventBus.publish(AgentObserverEvent.dispatched("agent-a", "input-a.txt"));
        eventBus.publish(AgentObserverEvent.stored("agent-a", "output-a.txt"));
        eventBus.publish(AgentObserverEvent.dispatched("agent-b", "input-b.txt"));
        eventBus.publish(AgentObserverEvent.stored("agent-b", "output-b.txt"));

        assertThat(events).hasSize(4);
        assertThat(events.stream().filter(e -> e.agentId().equals("agent-a")).count())
                .isEqualTo(2);
        assertThat(events.stream().filter(e -> e.agentId().equals("agent-b")).count())
                .isEqualTo(2);
    }

    // -- multiple publishes after unregistration --

    @Test
    void givenCallbackRemovedThenMultiplePublishes_ThenOnlyRemainingInvoked() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<AgentObserverEvent> first = e -> count.incrementAndGet();
        java.util.function.Consumer<AgentObserverEvent> second = e -> count.incrementAndGet();

        eventBus.registerCallback(first);
        eventBus.registerCallback(second);
        eventBus.unregisterCallback(first);

        eventBus.publish(AgentObserverEvent.dispatched("agent-1", "file.txt"));
        eventBus.publish(AgentObserverEvent.stored("agent-1", "output.txt"));

        assertThat(count).hasValue(2); // only second was invoked twice
    }
}
