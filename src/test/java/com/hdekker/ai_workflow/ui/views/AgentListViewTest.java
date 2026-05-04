package com.hdekker.ai_workflow.ui.views;

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.Test;

/**
 * Test class for AgentListView
 */
public class AgentListViewTest {

    @Test
    void testViewClassExists() {
        // Basic test to ensure the view class can be instantiated
        // We can't easily test Vaadin components without a full Vaadin environment
        assertDoesNotThrow(() -> {
            Class.forName("com.hdekker.ai_workflow.ui.views.AgentListView");
        });
    }

    @Test
    void testViewHasRouteAnnotation() {
        // Verify the view class is properly annotated with @Route
        Class<?> viewClass;
        try {
            viewClass = Class.forName("com.hdekker.ai_workflow.ui.views.AgentListView");
            assertNotNull(viewClass.getAnnotation(com.vaadin.flow.router.Route.class));
        } catch (ClassNotFoundException e) {
            fail("AgentListView class not found");
        }
    }
}
