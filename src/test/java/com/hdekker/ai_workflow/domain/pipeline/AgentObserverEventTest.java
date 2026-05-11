package com.hdekker.ai_workflow.domain.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AgentObserverEvent} factory methods and fields.
 * <p>
 * Verifies that the event record carries the correct fields for each
 * event type, especially that {@code FILTERED} events include the regex
 * that rejected the file while {@code DISPATCHED} and {@code STORED}
 * events carry {@code null} for the regex field.
 */
class AgentObserverEventTest {

    // -- dispatched event --

    @Test
    void whenCreateDispatchedEvent_ThenFieldsAreCorrect() {
        AgentObserverEvent event = AgentObserverEvent.dispatched("agent-1", "input.txt");

        assertThat(event.agentId()).isEqualTo("agent-1");
        assertThat(event.eventType()).isEqualTo(AgentObserverEventType.DISPATCHED);
        assertThat(event.fileName()).isEqualTo("input.txt");
        assertThat(event.regex()).isNull();
        assertThat(event.timestamp()).isNotNull();
    }

    // -- stored event --

    @Test
    void whenCreateStoredEvent_ThenFieldsAreCorrect() {
        AgentObserverEvent event = AgentObserverEvent.stored("agent-1", "output.txt");

        assertThat(event.agentId()).isEqualTo("agent-1");
        assertThat(event.eventType()).isEqualTo(AgentObserverEventType.STORED);
        assertThat(event.fileName()).isEqualTo("output.txt");
        assertThat(event.regex()).isNull();
        assertThat(event.timestamp()).isNotNull();
    }

    // -- filtered event --

    @Test
    void whenCreateFilteredEvent_ThenFieldsAreCorrect() {
        AgentObserverEvent event
                = AgentObserverEvent.filtered("agent-1", "notes.md", ".*\\.java");

        assertThat(event.agentId()).isEqualTo("agent-1");
        assertThat(event.eventType()).isEqualTo(AgentObserverEventType.FILTERED);
        assertThat(event.fileName()).isEqualTo("notes.md");
        assertThat(event.regex()).isEqualTo(".*\\.java");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void whenCreateFilteredEventWithNullRegex_ThenRegexIsNull() {
        AgentObserverEvent event = AgentObserverEvent.filtered("agent-1", "file.txt", null);

        assertThat(event.agentId()).isEqualTo("agent-1");
        assertThat(event.eventType()).isEqualTo(AgentObserverEventType.FILTERED);
        assertThat(event.fileName()).isEqualTo("file.txt");
        assertThat(event.regex()).isNull();
        assertThat(event.timestamp()).isNotNull();
    }

    // -- enum value existence --

    @Test
    void thenFilteredEnumValueExists() {
        assertThat(AgentObserverEventType.FILTERED).isNotNull();
        assertThat(AgentObserverEventType.values())
                .contains(AgentObserverEventType.DISPATCHED,
                        AgentObserverEventType.STORED,
                        AgentObserverEventType.FILTERED);
    }
}
