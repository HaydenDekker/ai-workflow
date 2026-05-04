package com.hdekker.ai_workflow.ui.views;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.ui.components.AgentCreationDialog;
import com.hdekker.ai_workflow.ui.service.AgentInfoService;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Dedicated test view for rapid prototyping of agent creation.
 * 
 * <p>Allows developers to test the agent creation dialog with dummy data
 * and visual confirmation via notification. Per ADR-003, this test-only view
 * is not added to the navigation bar and is accessed directly via URL.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Pre-filled form with dummy data</li>
 *   <li>Open Agent Creation Dialog button (pre-populated with dummy data)</li>
 *   <li>Simulate API Call button (makes real POST to /api/agents)</li>
 *   <li>Response display area showing status and agent details</li>
 * </ul>
 */
@Route("agents/create/test")
@PageTitle("Agent Creation Test")
public class AgentCreationTestView extends VerticalLayout {

    private static final String API_BASE_URL = "http://localhost:8080/api/agents";

    private final TextField titleField;
    private final TextField targetDirectoryField;
    private final TextField fileInputRegexField;
    private final ComboBox<String> agentTypeCombo;
    private final TextArea bodyField;
    private final TextArea outputStructureField;
    private final TextField outputFilenameTemplateField;

    private final Div statusDiv;
    private final Div agentIdDiv;
    private final Div sourceDiv;

    private final AgentInfoService agentInfoService;

    @Autowired
    public AgentCreationTestView(AgentInfoService agentInfoService) {
        this.agentInfoService = agentInfoService;
        addClassName("test-view");
        setPadding(true);
        setSpacing(true);
        setWidthFull();

        // Header
        H2 header = new H2("Agent Creation Test View");
        header.addClassName("page-title");

        // Form fields with dummy data
        titleField = new TextField("Title", "Test Agent");
        titleField.setPlaceholder("Enter agent title");
        titleField.addClassName("test-form-field");

        targetDirectoryField = new TextField("Target Directory", "/tmp/test-dir");
        targetDirectoryField.setPlaceholder("Enter target directory path");
        targetDirectoryField.addClassName("test-form-field");

        fileInputRegexField = new TextField("File Input Regex", ".*\\.java");
        fileInputRegexField.setPlaceholder("Enter file input regex");
        fileInputRegexField.addClassName("test-form-field");

        agentTypeCombo = new ComboBox<>("Agent Type", "Map", "Reduction", "Split");
        agentTypeCombo.setValue("Map");
        agentTypeCombo.setPlaceholder("Select agent type");
        agentTypeCombo.addClassName("test-form-field");

        bodyField = new TextArea("Body (Prompt)", "Process Java files and generate documentation");
        bodyField.setPlaceholder("Enter prompt body");
        bodyField.setHeight("80px");
        bodyField.addClassName("test-form-field");

        outputStructureField = new TextArea("Output Structure", "Generate summary with file paths and key points");
        outputStructureField.setPlaceholder("Enter output structure");
        outputStructureField.setHeight("80px");
        outputStructureField.addClassName("test-form-field");

        outputFilenameTemplateField = new TextField("Output Filename Template", "output/${name}.md");
        outputFilenameTemplateField.setPlaceholder("Enter output filename template");
        outputFilenameTemplateField.addClassName("test-form-field");

        // Buttons
        Button openDialogButton = new Button("Open Agent Creation Dialog", event -> openDialog());
        openDialogButton.addClassName("test-action-btn");
        openDialogButton.addThemeName("primary");

        Button simulateApiButton = new Button("Simulate API Call (POST /api/agents)", event -> simulateApiCall());
        simulateApiButton.addClassName("test-action-btn");
        simulateApiButton.addThemeName("secondary");

        // Form preview section
        Div formPreviewTitle = new Div();
        formPreviewTitle.setText("--- Form Preview ---");
        formPreviewTitle.addClassName("section-header");

        Div previewLayout = buildPreviewLayout();

        // Response section
        Div responseTitle = new Div();
        responseTitle.setText("--- Response ---");
        responseTitle.addClassName("section-header");

        statusDiv = new Div();
        statusDiv.addClassName("response-value");
        statusDiv.setText("Status: Not yet called");

        agentIdDiv = new Div();
        agentIdDiv.addClassName("response-value");
        agentIdDiv.setText("Agent ID: N/A");

        sourceDiv = new Div();
        sourceDiv.addClassName("response-value");
        sourceDiv.setText("Source: N/A");

        // Assemble layout
        add(header);
        add(openDialogButton);
        add(new Hr());
        add(formPreviewTitle);
        add(previewLayout);
        add(simulateApiButton);
        add(new Hr());
        add(responseTitle);
        add(statusDiv);
        add(agentIdDiv);
        add(sourceDiv);

        // Add field value listeners for live preview update
        titleField.addValueChangeListener(e -> updatePreview());
        targetDirectoryField.addValueChangeListener(e -> updatePreview());
        fileInputRegexField.addValueChangeListener(e -> updatePreview());
        agentTypeCombo.addValueChangeListener(e -> updatePreview());
        bodyField.addValueChangeListener(e -> updatePreview());
        outputStructureField.addValueChangeListener(e -> updatePreview());
        outputFilenameTemplateField.addValueChangeListener(e -> updatePreview());
    }

    /**
     * Opens the AgentCreationDialog pre-populated with current form values.
     * On submit, shows notification confirming the AgentDefinition.
     */
    private void openDialog() {
        AgentDefinition dummyDef = buildAgentDefinition();
        AgentCreationDialog dialog = new AgentCreationDialog(agentInfoService, agentDef -> {
            // Test view callback: show notification with the definition
            Notification.show(
                    "Agent created: " + agentDef.title(),
                    5000, Notification.Position.MIDDLE);
        });
        dialog.open(dummyDef);
    }

    /**
     * Simulates an API call by making a real POST to /api/agents
     * with the current form values.
     */
    private void simulateApiCall() {
        AgentDefinition definition = buildAgentDefinition();
        String json = buildJsonPayload(definition);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // Update UI on Vaadin UI thread
        getUI().ifPresent(ui -> ui.access(() -> {
            statusDiv.setText("Status: Sending request...");
        }));

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    String body = response.body();
                    int statusCode = response.statusCode();

                    // Update UI on Vaadin UI thread
                    getUI().ifPresent(ui -> ui.access(() -> {
                        if (statusCode == 200) {
                            statusDiv.setText("Status: " + statusCode + " OK");

                            // Parse agent ID and source from response
                            String agentId = extractField(body, "id");
                            String source = extractField(body, "source");
                            agentIdDiv.setText("Agent ID: " + (agentId != null ? agentId : "N/A"));
                            sourceDiv.setText("Source: " + (source != null ? source : "N/A"));

                            Notification.show(
                                    "Agent created successfully: " + agentId,
                                    5000, Notification.Position.MIDDLE);
                        } else {
                            statusDiv.setText("Status: " + statusCode + " - " + body);
                            agentIdDiv.setText("Agent ID: N/A");
                            sourceDiv.setText("Source: N/A");

                            Notification.show(
                                    "API call failed with status: " + statusCode,
                                    5000, Notification.Position.MIDDLE);
                        }
                    }));
                })
                .exceptionally(throwable -> {
                    getUI().ifPresent(ui -> ui.access(() -> {
                        statusDiv.setText("Status: Error - " + throwable.getMessage());
                        agentIdDiv.setText("Agent ID: N/A");
                        sourceDiv.setText("Source: N/A");

                        Notification.show(
                                "API call error: " + throwable.getMessage(),
                                5000, Notification.Position.MIDDLE);
                    }));
                    return null;
                });
    }

    /**
     * Builds an AgentDefinition from the current form values.
     */
    private AgentDefinition buildAgentDefinition() {
        return new AgentDefinition(
                fileInputRegexField.getValue().trim(),
                titleField.getValue().trim(),
                bodyField.getValue().trim(),
                agentTypeCombo.getValue(),
                outputStructureField.getValue().trim(),
                outputFilenameTemplateField.getValue().trim(),
                targetDirectoryField.getValue().trim()
        );
    }

    /**
     * Builds a simple JSON payload for the AgentDefinition.
     * Uses manual JSON construction to avoid adding Jackson dependency.
     */
    private String buildJsonPayload(AgentDefinition def) {
        return "{"
                + "\"fileInputRegex\":\"" + escapeJson(def.fileInputRegex()) + "\","
                + "\"title\":\"" + escapeJson(def.title()) + "\","
                + "\"body\":\"" + escapeJson(def.body()) + "\","
                + "\"agentType\":\"" + escapeJson(def.agentType()) + "\","
                + "\"outputStructure\":\"" + escapeJson(def.outputStructure()) + "\","
                + "\"outputFilenameTemplate\":\"" + escapeJson(def.outputFilenameTemplate()) + "\""
                + "}";
    }

    /**
     * Escapes special characters for JSON string values.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Extracts a field value from a simple JSON response.
     */
    private String extractField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) {
            return null;
        }
        int valueStart = idx + key.length();
        // Skip whitespace
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) {
            return null;
        }
        char quote = json.charAt(valueStart);
        if (quote == '"') {
            // String value
            int end = json.indexOf('"', valueStart + 1);
            return end == -1 ? null : json.substring(valueStart + 1, end);
        } else if (quote == 't' || quote == 'f') {
            // Boolean
            int end = json.indexOf(',', valueStart);
            if (end == -1) {
                end = json.indexOf('}', valueStart);
            }
            return end == -1 ? null : json.substring(valueStart, end).trim();
        } else if (Character.isDigit(quote) || quote == '-') {
            // Number
            int end = valueStart;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
                end++;
            }
            return json.substring(valueStart, end);
        }
        return null;
    }

    /**
     * Builds the preview layout with current form values.
     */
    private Div buildPreviewLayout() {
        Div previewLayout = new Div();
        previewLayout.addClassName("preview-grid");

        previewLayout.add(buildPreviewRow("Title:", titleField.getValue()));
        previewLayout.add(buildPreviewRow("Target Directory:", targetDirectoryField.getValue()));
        previewLayout.add(buildPreviewRow("File Input Regex:", fileInputRegexField.getValue()));
        previewLayout.add(buildPreviewRow("Agent Type:", agentTypeCombo.getValue()));
        previewLayout.add(buildPreviewRow("Body:", bodyField.getValue()));
        previewLayout.add(buildPreviewRow("Output Structure:", outputStructureField.getValue()));
        previewLayout.add(buildPreviewRow("Output Filename:", outputFilenameTemplateField.getValue()));

        return previewLayout;
    }

    /**
     * Builds a single preview row.
     */
    private Div buildPreviewRow(String label, String value) {
        Div row = new Div();
        row.addClassName("preview-row");
        Div labelDiv = new Div(label);
        labelDiv.addClassName("preview-label");
        Div valueDiv = new Div(escapeHtml(value));
        valueDiv.addClassName("preview-value");
        row.add(labelDiv, valueDiv);
        return row;
    }

    /**
     * Updates the preview div with current form values.
     */
    private void updatePreview() {
        // Replace the old preview with new one
        getChildren()
                .filter(child -> child.getElement().hasAttribute("class")
                        && child.getElement().getAttribute("class").contains("preview-grid"))
                .findFirst()
                .ifPresent(oldPreview -> {
                    remove(oldPreview);
                    add(buildPreviewLayout());
                });
    }

    /**
     * Escapes HTML special characters to prevent XSS in preview display.
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
