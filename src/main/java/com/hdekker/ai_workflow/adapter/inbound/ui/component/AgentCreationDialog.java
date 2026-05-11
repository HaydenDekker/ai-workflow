package com.hdekker.ai_workflow.adapter.inbound.ui.component;

import java.util.function.Consumer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.inbound.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Reusable modal dialog for creating new agents from the Agent ListView.
 * 
 * <p>Uses Vaadin {@code Dialog} with {@code FormLayout} for form validation,
 * keyboard support, and proper lifecycle handling. Per ADR-002, Vaadin
 * pre-built dialogs are the standard pattern.</p>
 * 
 * <p>Validation rules:</p>
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
public class AgentCreationDialog extends Dialog {

    private static final Logger log = LoggerFactory.getLogger(AgentCreationDialog.class);

    private static final String[] AGENT_TYPES =
            java.util.Arrays.stream(AgentType.values())
                    .map(AgentType::getAsString)
                    .toArray(String[]::new);

    private final TextField titleField;
    private final TextField targetDirectoryField;
    private final TextField fileInputRegexField;
    private final ComboBox<String> agentTypeCombo;
    private final TextArea bodyField;
    private final TextArea outputStructureField;
    private final TextField outputFilenameTemplateField;
    private final Button cancelButton;
    private final Button createButton;

    private final AgentInfoService agentInfoService;
    private final Consumer<AgentDefinition> onConfirm;

    /**
     * Creates a new agent creation dialog.
     *
     * @param agentInfoService the service used to create agents
     */
    public AgentCreationDialog(AgentInfoService agentInfoService) {
        this(agentInfoService, null);
    }

    /**
     * Creates a new agent creation dialog with a custom confirm callback.
     *
     * @param agentInfoService the service used to create agents
     * @param onConfirm        callback invoked with the populated AgentDefinition on successful create
     */
    public AgentCreationDialog(AgentInfoService agentInfoService, Consumer<AgentDefinition> onConfirm) {
        this.agentInfoService = agentInfoService;
        this.onConfirm = onConfirm != null ? onConfirm : this::defaultOnConfirm;

        setModal(true);
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);
        setWidth("650px");
        setHeight("750px");

        // Initialize form fields
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

        // Initialize buttons
        cancelButton = new Button("Cancel", event -> close());
        cancelButton.addClassName("dialog-cancel-btn");

        createButton = new Button("Create Agent", event -> handleCreate());
        createButton.addClassName("dialog-create-btn");
        createButton.addThemeName("primary");

        // Build form layout
        FormLayout formLayout = new FormLayout();
        formLayout.addClassName("agent-creation-form");
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("650px", 1)
        );
        formLayout.add(titleField, targetDirectoryField, fileInputRegexField, agentTypeCombo,
                bodyField, outputStructureField, outputFilenameTemplateField);

        // Build button bar
        HorizontalLayout buttonBar = new HorizontalLayout(cancelButton, createButton);
        buttonBar.addClassName("dialog-button-bar");
        buttonBar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);
        buttonBar.setAlignItems(Alignment.BASELINE);

        // Add components to dialog content
        add(formLayout, buttonBar);

        // Focus title field when opened
        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                titleField.focus();
            }
        });
    }

    /**
     * Default confirmation handler: creates agent via service and shows notification.
     */
    private void defaultOnConfirm(AgentDefinition agentDefinition) {
        agentInfoService.createAgent(agentDefinition).subscribe(
                info -> {
                    Notification.show(
                            "Agent created: " + agentDefinition.title(),
                            5000, Notification.Position.MIDDLE);
                },
                error -> {
                    log.error("Failed to create agent", error);
                    Notification.show(
                            "Error creating agent: " + error.getMessage(),
                            5000, Notification.Position.MIDDLE);
                }
        );
    }

    /**
     * Opens the dialog, optionally pre-populating with an existing agent definition.
     *
     * @param existing the existing agent definition to pre-fill (null for new agent)
     */
    public void open(AgentDefinition existing) {
        if (existing != null) {
            titleField.setValue(existing.title());
            targetDirectoryField.setValue(existing.targetDirectory() != null ? existing.targetDirectory() : "");
            fileInputRegexField.setValue(existing.fileInputRegex());
            agentTypeCombo.setValue(existing.agentType().getAsString());
            bodyField.setValue(existing.body());
            outputStructureField.setValue(existing.outputStructure());
            outputFilenameTemplateField.setValue(existing.outputFilenameTemplate());
        } else {
            resetForm();
        }
        super.open();
    }

    /**
     * Opens the dialog (alias for {@link #open(AgentDefinition)} with null).
     */
    public void open() {
        open(null);
    }

    /**
     * Resets the form to default values.
     */
    public void resetForm() {
        titleField.setValue("");
        targetDirectoryField.setValue("");
        fileInputRegexField.setValue(".*");
        agentTypeCombo.setValue(null);
        bodyField.setValue("");
        outputStructureField.setValue("");
        outputFilenameTemplateField.setValue("output/${name}.md");
    }

    /**
     * Handles the Create Agent button click: validates all fields,
     * then either shows error notification or calls the confirm callback.
     */
    private void handleCreate() {
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

        // Build AgentDefinition from form values
        AgentDefinition definition = new AgentDefinition(
                fileInputRegexField.getValue().trim(),
                titleField.getValue().trim(),
                bodyField.getValue().trim(),
                AgentType.fromString(agentTypeCombo.getValue()),
                outputStructureField.getValue().trim(),
                outputFilenameTemplateField.getValue().trim(),
                targetDirectoryField.getValue().trim()
        );

        // Call confirm callback
        onConfirm.accept(definition);

        // Close dialog and reset form
        close();
    }

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
        // Check if it's an absolute path
        if (!java.nio.file.Paths.get(value).isAbsolute()) {
            return new ValidationResult(false, "Must be an absolute path");
        }
        // Check if directory exists
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

    private TextField createTextField(String label, String defaultValue, int maxLength) {
        TextField field = new TextField(label, defaultValue);
        field.setPlaceholder("Enter " + label.toLowerCase());
        field.setMaxLength(maxLength);
        field.setRequired(true);
        field.addClassName("agent-creation-field");
        return field;
    }

    private TextArea createTextArea(String label, String defaultValue) {
        TextArea area = new TextArea(label, defaultValue);
        area.setPlaceholder("Enter " + label.toLowerCase());
        area.setHeight("120px");
        area.setMinHeight("60px");
        area.setMaxHeight("300px");
        area.setRequired(true);
        area.addClassName("agent-creation-textarea");
        return area;
    }

    private void addHelperText(TextField field, String helperText) {
        Div helper = new Div();
        helper.setText(helperText);
        helper.addClassName("field-helper-text");
        // Append helper after the field's input element
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
