package com.hdekker.ai_workflow.ui.views;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for PipelineInfoListView
 */
public class PipelineInfoListViewTest {

    @Test
    void testViewClassExists() {
        // Basic test to ensure the view class can be instantiated
        // We can't easily test Vaadin components without a full Vaadin environment
        assertDoesNotThrow(() -> {
            Class.forName("com.hdekker.ai_workflow.ui.views.PipelineInfoListView");
        });
    }

    @Test
    void testViewHasRouteAnnotation() {
        // Verify the view class is properly annotated with @Route
        Class<?> viewClass;
        try {
            viewClass = Class.forName("com.hdekker.ai_workflow.ui.views.PipelineInfoListView");
            assertNotNull(viewClass.getAnnotation(com.vaadin.flow.router.Route.class));
        } catch (ClassNotFoundException e) {
            fail("PipelineInfoListView class not found");
        }
    }
}