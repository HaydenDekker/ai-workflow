package com.hdekker.ai_workflow.adapter.inbound.ui.component;

import java.util.function.Consumer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Reusable modal dialog for editing and deleting existing agents from the Agent ListView.
 * 
 * <p>Uses Vaadin {@code Dialog} with {@code FormLayout} for form validation.
 * Displays all editable agent fields plus read-only metadata (created date, active status,
 * source). Provides Save and Delete action buttons.</p>
 * 
 * <p>Validation rules match {@link AgentCreationDialog}:</p>
 * <ul>
 *   <li>Title: required, max 100 chars</li>
 *   <li>Target Directory: required, absolute path, must exist</li>
 *   <li>File Input Regex: required, valid Java regex</li>
 *   <li>Agent Type: required (Map / Reduction / Split)</li>
 *   <li>Body: required, non-blank</li>
 *   <li>Output Structure: required, non-blank</li>
 *   <li>Output Filename Template: required, non-blank</li>
 * </ul>
 */
public class AgentDetailDialog extends Dialog {

    private static final Logger log = LoggerFactory.getLogger(AgentDetailDialog.class);

    private static final String[] AGENT_TYPES = {"Map", "Reduction", "Split"};

    private final TextField titleField;
    private final TextField targetDirectoryField;
    private final TextField fileInputRegexField;
    private final ComboBox<String> agentTypeCombo;
    private final TextArea bodyField;
    private final TextArea outputStructureField;
    private final TextField outputFilenameTemplateField;
    private final TextField createdAtField;
    private final TextField activeField;
    private final TextField sourceField;

    private final Button cancelButton;
    private final Button saveButton;
    private final Button deleteButton;

    private final AgentInfoService agentInfoService;
    private final AgentInfo existingAgent;
    private final Consumer<AgentInfo> onSave;
    private final Consumer<String> onDelete;

    /**
     * Creates a new agent detail dialog.
     *
     * @param agentInfoService the service used to update/delete agents
     * @param agentInfo        the agent to edit (must not be null)
     * @param onSave           callback invoked with the updated AgentInfo on successful save
     * @param onDelete         callback invoked with the deleted agent ID on successful delete
     */
    public AgentDetailDialog(AgentInfoService agentInfoService,
                             AgentInfo agentInfo,
                             Consumer<AgentInfo> onSave,
                             Consumer<String> onDelete) {
        this.agentInfoService = agentInfoService;
        this.existingAgent = agentInfo;
        this.onSave = onSave;
        this.onDelete = onDelete;

        setModal(true);
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);
        setWidth("650px");
        setHeight("800px");

        // Initialize editable form fields
        titleField = createTextField("Title", "", 100);
        targetDirectoryField = createTextField("Target Directory", "", 255);
        addHelperText(targetDirectoryField, "Absolute path to the directory to scan (e.g., /data/inbox)");
        fileInputRegexField = createTextField("File Input Regex", ".*", 100);
        addHelperText(fileInputRegexField, "Pattern: (?:(.*/)?)(.*)");

        agentTypeCombo = new ComboBox<>("Agent Type", AGENT_TYPES);
        agentTypeCombo.setRequired(true);
        agentTypeCombo.setPlaceholder("Select agent type");

        bodyField = createTextArea("Body (Prompt)", "");
        outputStructureField = createTextArea("Output Structure", "");
        outputFilenameTemplateField = createTextField("Output Filename Template", "output/${name}.md", 100);

        // Initialize read-only metadata fields
        createdAtField = new TextField("Created");
        createdAtField.setReadOnly(true);
        createdAtField.addClassName("readonly-field");

        activeField = new TextField("Active");
        activeField.setReadOnly(true);
        activeField.addClassName("readonly-field");

        sourceField = new TextField("Source");
        sourceField.setReadOnly(true);
        sourceField.addClassName("readonly-field");

        // Initialize buttons
        cancelButton = new Button("Cancel", event -> close());
        cancelButton.addClassName("dialog-cancel-btn");

        saveButton = new Button("Save", event -> handleSave());
        saveButton.addClassName("dialog-save-btn");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setIcon(new Icon(VaadinIcon.CHECK));

        deleteButton = new Button("Delete Agent", event -> handleDelete());
        deleteButton.addClassName("dialog-delete-btn");
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.setIcon(new Icon(VaadinIcon.TRASH));

        // Build form layout
        FormLayout formLayout = new FormLayout();
        formLayout.addClassName("agent-detail-form");
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("650px", 1)
        );
        formLayout.add(titleField, targetDirectoryField, fileInputRegexField, agentTypeCombo,
                bodyField, outputStructureField, outputFilenameTemplateField);

        // Build read-only metadata section
        HorizontalLayout metadataLayout = new HorizontalLayout();
        metadataLayout.setClassName("agent-detail-metadata");
        metadataLayout.setSpacing(true);
        metadataLayout.setPadding(false);
        metadataLayout.add(createdAtField, activeField, sourceField);

        // Build header with agent ID
        Div headerTitle = new Div();
        headerTitle.addClassName("dialog-header-title");
        if (existingAgent.id() != null) {
            headerTitle.setText("Edit Agent — " + existingAgent.id());
        } else {
            headerTitle.setText("Edit Agent");
        }

        // Build button bar
        HorizontalLayout buttonBar = new HorizontalLayout(cancelButton, saveButton, deleteButton);
        buttonBar.addClassName("dialog-button-bar");
        buttonBar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        buttonBar.setAlignItems(Alignment.BASELINE);

        // Add components to dialog content
        add(headerTitle, formLayout, new Hr(), metadataLayout, buttonBar);
    }

    /**
     * Opens the dialog, pre-populating all fields with the existing agent's data.
     *
     * @param agentInfo the agent to edit
     */
    public void open() {
        if (existingAgent != null && existingAgent.definition() != null) {
            AgentDefinition def = existingAgent.definition();
            titleField.setValue(def.title() != null ? def.title() : "");
            targetDirectoryField.setValue(def.targetDirectory() != null ? def.targetDirectory() : "");
            fileInputRegexField.setValue(def.fileInputRegex() != null ? def.fileInputRegex() : ".*");
            agentTypeCombo.setValue(def.agentType() != null ? def.agentType() : "Map");
            bodyField.setValue(def.body() != null ? def.body() : "");
            outputStructureField.setValue(def.outputStructure() != null ? def.outputStructure() : "");
            outputFilenameTemplateField.setValue(def.outputFilenameTemplate() != null ? def.outputFilenameTemplate() : "output/${name}.md");

            // Pre-populate read-only metadata
            if (existingAgent.createdAt() != null) {
                createdAtField.setValue(existingAgent.createdAt().toString());
            }
            activeField.setValue(Boolean.toString(existingAgent.active()));
            sourceField.setValue(existingAgent.source() != null ? existingAgent.source() : "N/A");
        }

        super.open();
    }

    /**
     * Handles the Save button click: validates all fields,
     * then sends the updated definition via the service.
     */
    private void handleSave() {
        // Validate all fields
        boolean hasErrors = false;

        ValidationResult titleResult = validateTitle();
        if (!titleResult.ok) {
            titleField.setErrorMessage(titleResult.message);
            hasErrors = true;
        }

        ValidationResult targetDirResult = validateTargetDirectory();
        if (!targetDirResult.ok) {
            targetDirectoryField.setErrorMessage(targetDirResult.message);
            hasErrors = true;
        }

        ValidationResult regexResult = validateFileInputRegex();
        if (!regexResult.ok) {
            fileInputRegexField.setErrorMessage(regexResult.message);
            hasErrors = true;
        }

        ValidationResult typeResult = validateAgentType();
        if (!typeResult.ok) {
            agentTypeCombo.setErrorMessage(typeResult.message);
            hasErrors = true;
        }

        ValidationResult bodyResult = validateBody();
        if (!bodyResult.ok) {
            bodyField.setErrorMessage(bodyResult.message);
            hasErrors = true;
        }

        ValidationResult outputResult = validateOutputStructure();
        if (!outputResult.ok) {
            outputStructureField.setErrorMessage(outputResult.message);
            hasErrors = true;
        }

        ValidationResult templateResult = validateOutputFilenameTemplate();
        if (!templateResult.ok) {
            outputFilenameTemplateField.setErrorMessage(templateResult.message);
            hasErrors = true;
        }

        if (hasErrors) {
            Notification.show("Please fix the errors in the form", 3000, Notification.Position.MIDDLE);
            return;
        }

        // Build updated AgentDefinition from form values
        AgentDefinition updatedDefinition = new AgentDefinition(
                fileInputRegexField.getValue().trim(),
                titleField.getValue().trim(),
                bodyField.getValue().trim(),
                agentTypeCombo.getValue(),
                outputStructureField.getValue().trim(),
                outputFilenameTemplateField.getValue().trim(),
                targetDirectoryField.getValue().trim()
        );

        // Send update via service
        agentInfoService.updateAgent(existingAgent.id(), updatedDefinition).subscribe(
                info -> {
                    Notification.show(
                            "Agent updated: " + updatedDefinition.title(),
                            5000, Notification.Position.MIDDLE);
                    if (onSave != null) {
                        onSave.accept(info);
                    }
                    close();
                },
                error -> {
                    log.error("Failed to update agent", error);
                    Notification.show(
                            "Error updating agent: " + error.getMessage(),
                            5000, Notification.Position.MIDDLE);
                }
        );
    }

    /**
     * Handles the Delete button click: shows a confirmation dialog,
     * then sends the delete request via the service.
     */
    private void handleDelete() {
        String agentName = existingAgent.definition() != null 
                ? existingAgent.definition().title() 
                : existingAgent.id();

        Dialog confirmDialog = new Dialog();
        confirmDialog.setModal(true);
        confirmDialog.setCloseOnEsc(false);
        confirmDialog.setCloseOnOutsideClick(false);
        confirmDialog.setWidth("400px");

        Div message = new Div();
        message.setText("Are you sure you want to delete agent \"" + agentName + "\"?\n\n"
                + "This will permanently remove the agent, its scanner, and all associated data.");
        message.getStyle().set("white-space", "pre-line");
        message.getStyle().set("margin-bottom", "16px");

        Button confirmBtn = new Button("Delete", event -> {
            confirmDialog.close();
            close();
            performDelete();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmBtn.addClassName("dialog-confirm-delete-btn");

        Button cancelBtn = new Button("Cancel", event -> confirmDialog.close());

        HorizontalLayout btnBar = new HorizontalLayout(cancelBtn, confirmBtn);
        btnBar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);

        confirmDialog.add(message, btnBar);
        confirmDialog.open();
    }

    /**
     * Performs the actual delete operation via the service.
     * The reactive callback queues the reload via UI.access() so it runs on
     * the Vaadin UI thread and triggers push to the browser when the session
     * unlocks.
     */
    private void performDelete() {
        final String agentId = existingAgent.id();

        agentInfoService.deleteAgent(agentId).subscribe(
                deletedId -> {
                    log.info("Agent deleted: {} - refreshing grid", deletedId);
                    Notification.show(
                            "Agent deleted: " + deletedId,
                            5000, Notification.Position.MIDDLE);
                    if (onDelete != null) {
                        // Queue reload on Vaadin UI thread. The session unlock
                        // (triggered when this method returns) will process this
                        // queued callback and push the grid update to the browser.
                        com.vaadin.flow.component.UI.getCurrent()
                                .access(() -> {
                                    log.info("Reload callback executing - refreshing agent grid");
                                    onDelete.accept(deletedId);
                                });
                    }
                },
                error -> {
                    log.error("Failed to delete agent: {}", agentId, error);
                    Notification.show(
                            "Error deleting agent: " + error.getMessage(),
                            5000, Notification.Position.MIDDLE);
                }
        );
    }

    // --- Validation methods (same as AgentCreationDialog) ---

    private ValidationResult validateTitle() {
        String value = titleField.getValue();
        if (value == null || value.isBlank()) {
            return new ValidationResult(false, "Title is required");
        }
        if (value.length() > 100) {
            return new ValidationResult(false, "Title must be at most 100 characters");
        }
        return new ValidationResult(true, null);
    }

    private ValidationResult validateTargetDirectory() {
        String value = targetDirectoryField.getValue();
        if (value == null || value.isBlank()) {
            return new ValidationResult(false, "Target directory is required");
        }
        if (!java.nio.file.Paths.get(value).isAbsolute()) {
            return new ValidationResult(false, "Must be an absolute path");
        }
        java.nio.file.Path path = java.nio.file.Paths.get(value);
        if (!java.nio.file.Files.exists(path)) {
            return new ValidationResult(false, "Directory does not exist: " + value);
        }
        if (!java.nio.file.Files.isDirectory(path)) {
            return new ValidationResult(false, "Not a directory: " + value);
        }
        if (!java.nio.file.Files.isReadable(path)) {
            return new ValidationResult(false, "Directory is not readable: " + value);
        }
        return new ValidationResult(true, null);
    }

    private ValidationResult validateFileInputRegex() {
        String value = fileInputRegexField.getValue();
        if (value == null || value.isBlank()) {
            return new ValidationResult(false, "File input regex is required");
        }
        try {
            java.util.regex.Pattern.compile(value);
            return new ValidationResult(true, null);
        } catch (java.util.regex.PatternSyntaxException e) {
            return new ValidationResult(false, "Invalid regex pattern: " + e.getDescription());
        }
    }

    private ValidationResult validateAgentType() {
        if (agentTypeCombo.getValue() == null || agentTypeCombo.getValue().isBlank()) {
            return new ValidationResult(false, "Agent type is required");
        }
        return new ValidationResult(true, null);
    }

    private ValidationResult validateBody() {
        String value = bodyField.getValue();
        if (value == null || value.isBlank()) {
            return new ValidationResult(false, "Body is required");
        }
        return new ValidationResult(true, null);
    }

    private ValidationResult validateOutputStructure() {
        String value = outputStructureField.getValue();
        if (value == null || value.isBlank()) {
            return new ValidationResult(false, "Output structure is required");
        }
        return new ValidationResult(true, null);
    }

    private ValidationResult validateOutputFilenameTemplate() {
        String value = outputFilenameTemplateField.getValue();
        if (value == null || value.isBlank()) {
            return new ValidationResult(false, "Output filename template is required");
        }
        return new ValidationResult(true, null);
    }

    // --- Field creation helpers ---

    private TextField createTextField(String label, String defaultValue, int maxLength) {
        TextField field = new TextField(label, defaultValue);
        field.setPlaceholder("Enter " + label.toLowerCase());
        field.setMaxLength(maxLength);
        field.setRequired(true);
        field.addClassName("agent-detail-field");
        return field;
    }

    private TextArea createTextArea(String label, String defaultValue) {
        TextArea area = new TextArea(label, defaultValue);
        area.setPlaceholder("Enter " + label.toLowerCase());
        area.setHeight("120px");
        area.setMinHeight("60px");
        area.setMaxHeight("300px");
        area.setRequired(true);
        area.addClassName("agent-detail-textarea");
        return area;
    }

    private void addHelperText(TextField field, String helperText) {
        Div helper = new Div();
        helper.setText(helperText);
        helper.addClassName("field-helper-text");
        field.getElement().appendChild(helper.getElement());
    }

    /**
     * Simple validation result holder.
     */
    private static class ValidationResult {
        final boolean ok;
        final String message;

        ValidationResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }
}
