package com.hdekker.ai_workflow.test.pipeline.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration class for ChatClient mocks using builder pattern.
 * Supports different behaviors and response sequences for testing.
 */
public class MockConfiguration {
    private final List<String> responses;
    private final MockBehavior behavior;
    private final Map<String, Object> properties;

    private MockConfiguration(List<String> responses, MockBehavior behavior, Map<String, Object> properties) {
        this.responses = new ArrayList<>(responses);
        this.behavior = behavior;
        this.properties = new HashMap<>(properties);
    }

    public List<String> getResponses() {
        return responses;
    }

    public MockBehavior getBehavior() {
        return behavior;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public static Builder builder() {
        return new Builder();
    }

    public enum MockBehavior {
        SUCCESS,
        ERROR,
        TIMEOUT,
        EMPTY_RESPONSE
    }

    public static class Builder {
        private List<String> responses = new ArrayList<>();
        private MockBehavior behavior = MockBehavior.SUCCESS;
        private Map<String, Object> properties = new HashMap<>();
        private boolean capturePrompts = false;
        private List<String> promptCaptureList = null;

        public Builder responses(List<String> responses) {
            this.responses = new ArrayList<>(responses);
            return this;
        }

        public Builder response(String response) {
            this.responses = List.of(response);
            return this;
        }

        public Builder behavior(MockBehavior behavior) {
            this.behavior = behavior;
            return this;
        }

        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder capturePrompts(List<String> promptList) {
            this.capturePrompts = true;
            this.promptCaptureList = promptList;
            return this;
        }

        public MockConfiguration build() {
            properties.put("capturePrompts", capturePrompts);
            properties.put("promptList", promptCaptureList);
            return new MockConfiguration(responses, behavior, properties);
        }
    }
}