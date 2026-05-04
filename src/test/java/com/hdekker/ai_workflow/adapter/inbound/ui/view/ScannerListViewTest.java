package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.Test;

/**
 * Test class for ScannerListView.
 */
public class ScannerListViewTest {

    @Test
    void testViewClassExists() {
        assertDoesNotThrow(() -> {
            Class.forName("com.hdekker.ai_workflow.adapter.inbound.ui.view.ScannerListView");
        });
    }

    @Test
    void testViewHasRouteAnnotation() {
        Class<?> viewClass;
        try {
            viewClass = Class.forName("com.hdekker.ai_workflow.adapter.inbound.ui.view.ScannerListView");
            assertNotNull(viewClass.getAnnotation(com.vaadin.flow.router.Route.class));
            com.vaadin.flow.router.Route route = viewClass.getAnnotation(com.vaadin.flow.router.Route.class);
            assertEquals("scanners", route.value());
        } catch (ClassNotFoundException e) {
            fail("ScannerListView class not found");
        }
    }

    @Test
    void testViewHasPageTitleAnnotation() {
        Class<?> viewClass;
        try {
            viewClass = Class.forName("com.hdekker.ai_workflow.adapter.inbound.ui.view.ScannerListView");
            assertNotNull(viewClass.getAnnotation(com.vaadin.flow.router.PageTitle.class));
            com.vaadin.flow.router.PageTitle pageTitle = viewClass.getAnnotation(com.vaadin.flow.router.PageTitle.class);
            assertEquals("Scanners", pageTitle.value());
        } catch (ClassNotFoundException e) {
            fail("ScannerListView class not found");
        }
    }
}
