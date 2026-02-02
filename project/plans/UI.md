# UI Plan for `AgentDefinition` Records

This document contains the detailed, non‑technical plan for a form‑based UI that allows users to create, edit, view, and delete `AgentDefinition` records in the **PipelineRestController**.

---

## 1.  What `AgentDefinition` looks like

```java
record AgentDefinition(
        String fileInputRegex,          // Regex to match file URLs
        String title,                  // Human‑readable name
        String body,                   // Prompt body / instructions
        String agentType,              // Type of pipeline agent
        String outputStructure,        // JSON/YAML description of output
        String outputFilenameTemplate  // Template for generated file names
);
```

## 2.  UI Components

| Property | Component | Visual style | Helper text |
|----------|-----------|--------------|-------------|
| fileInputRegex | Text input | 1‑line rectangle, light‑gray background | “Enter Java/PCRE‑style regex.” |
| title | Text input | 1‑line, placeholder *Your agent title* | “Short and descriptive.” |
| body | Text area | Tall box, monospace font (optional syntax highlight) | “Describe the prompt to be sent.” |
| agentType | Drop‑down | Rounded, list of agent types | “Choose the processing strategy.” |
| outputStructure | Code text area | Monospace, syntax‑highlight if possible | “Define the JSON/YAML of the return.” |
| outputFilenameTemplate | Text input | 1‑line, placeholder *{timestamp}_{name}.json* | “Use ${param} markers.” |

*Optional extras:*
- **Regex tester** button that opens a small modal to test the regex against a sample URL.
- **Live preview** of a sample match.
- **Save‑draft** auto‑save to `sessionStorage` on form changes.

## 3.  High‑Level Flow & REST Mapping

| Step | UI View | URL | REST Call | Notes |
|------|---------|-----|-----------|-------|
| List agents | Table showing *Title*, *Agent Type*, *File Regex* | `/agents` | `GET /agents` | Default landing page |
| Create new | Empty form | `/agents/new` | `POST /agents` | Creates a new record |
| Edit existing | Form pre‑filled with selected agent | `/agents/{id}/edit` | `PUT /agents/{id}` | Replaces full record |
| View details | Read‑only panel (body, structure) | `/agents/{id}` | `GET /agents/{id}` | Shows full record |
| Delete | Confirmation modal | – | `DELETE /agents/{id}` | Removes record |

REST payload is a direct JSON mapping of the record fields.

## 4.  Browser State & UX Touch‑Points

- **Listing** – `/agents` shows all agents.
- **Add** – `/agents/new` shows the form.
- **Edit** – `/agents/123/edit` loads record 123.
- **Details** – `/agents/123` read‑only view.
- **Unsaved changes** – if form is dirty and the user navigates away, a browser warning appears.

All forms support **client‑side validation**:
- `fileInputRegex` must be a valid regex.
- `title` is required.
- `agentType` is required.
- `outputStructure`, if supplied, must be syntactically correct JSON/YAML.
- `outputFilenameTemplate` can be left blank.

## 5.  Suggested CSS / Color Palette

- **Background**: Very light gray (≈#F9F9F9) in light mode, charcoal (#212121) in dark mode.
- **Primary button**: Bright blue (#007BFF) in light mode, teal (#17A2B8) in dark mode.
- **Secondary button**: Gray (#6C757D). Disabled: light gray (#E0E0E0).
- **Accent**: Light blue tint (#D0E9FF) for focused fields.
- **Error**: Pale red (#F8D7DA) background, red text (#A71D2A).
- **Success**: Soft green (#D4EDDA) background, green text (#155724).

All elements should have 4‑space indentation in code editors and no tabs. Use consistent 4‑space margins for layout padding.

## 6.  Interaction with `PipelineRestController`

The controller exposes:

| Endpoint | Method | Payload |
|----------|--------|---------|
| `/agents` | GET | N/A – returns JSON array of `AgentDefinition` objects. |
| `/agents/{id}` | GET | N/A – returns single record. |
| `/agents` | POST | JSON representation of a new record (without `id`). |
| `/agents/{id}` | PUT | JSON representation of the updated record. |
| `/agents/{id}` | DELETE | N/A |

The UI serialises each property into JSON; no transformation is required beyond standard JSON.

## 7.  Next Steps for Implementation

1. **Create the `plans/` directory** (if not already present). | 
2. **Add `UI.md`** with this content. | 
3. Start building the Vue/React/Vaadin component, wiring it to the above endpoints. |

---

> **Note**: If there are existing design tokens or style guides, map the colors above to those tokens.

Happy coding!