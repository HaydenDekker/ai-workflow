# Sub-Plan: Initial AgentDefinition Grid View Implementation

## Overview
Create an initial Vaadin Flow view that displays existing AgentDefinitions in a grid layout, following the existing project structure and Vaadin configuration already present in the project.

## Current Project State Analysis
- ✅ Vaadin Flow 24.9.4 already configured in pom.xml
- ✅ AgentDefinition record exists at `src/main/java/com/hdekker/ai_workflow/pipeline/domain/AgentDefinition.java`
- ✅ PipelineRestController exists with endpoints for CRUD operations
- ❌ No existing Vaadin views or UI components
- ❌ REST endpoints are at `/api/pipelines`, not `/agents` as specified in UI.md

## Implementation Plan

### Phase 1: Basic View Structure Setup
**Objective**: Create the basic Vaadin Flow view structure and routing

#### Checklist:
- [ ] Create `AgentDefinitionListView` class extending `VerticalLayout`
- [ ] Create `views` package under `com.hdekker.ai_workflow.ui`
- [ ] Configure main route with `@Route("")` for default landing page
- [ ] Add basic view title and layout structure
- [ ] Set up responsive layout with proper spacing

#### Files to create:
- `src/main/java/com/hdekker/ai_workflow/ui/views/AgentDefinitionListView.java`

### Phase 2: Grid Component Integration
**Objective**: Implement Vaadin Grid to display AgentDefinitions

#### Checklist:
- [ ] Add Vaadin Grid component to the view
- [ ] Configure Grid columns for AgentDefinition fields:
  - Title (String)
  - Agent Type (String) 
  - File Input Regex (String)
  - Output Filename Template (String)
- [ ] Set up proper column widths and sorting
- [ ] Add basic styling for the grid
- [ ] Implement responsive grid behavior

#### Grid columns configuration:
```java
grid.addColumn(AgentDefinition::title).setHeader("Title").setAutoWidth(true);
grid.addColumn(AgentDefinition::agentType).setHeader("Agent Type").setAutoWidth(true);
grid.addColumn(AgentDefinition::fileInputRegex).setHeader("File Regex").setAutoWidth(true);
grid.addColumn(AgentDefinition::outputFilenameTemplate).setHeader("Output Template").setAutoWidth(true);
```

### Phase 3: Data Integration
**Objective**: Connect the grid to the PipelineRestController endpoints

#### Checklist:
- [ ] Create `AgentDefinitionService` to handle REST API calls
- [ ] Implement HTTP client using Spring's `WebClient` or `RestTemplate`
- [ ] Map PipelineRestController endpoints to service methods:
  - `GET /api/pipelines` → `getAllAgentDefinitions()`
- [ ] Handle error cases (empty response, network errors)
- [ ] Transform `PipelineInfo` objects to `AgentDefinition` objects
- [ ] Add loading indicators during data fetch
- [ ] Implement automatic data refresh on view load

#### Service method mapping:
```java
// PipelineRestController returns List<PipelineInfo>
// Need to extract AgentDefinition from PipelineInfo objects
```

### Phase 4: View Navigation and Basic Interactions
**Objective**: Add navigation capabilities and basic grid interactions

#### Checklist:
- [ ] Add "New Agent" button to navigate to creation form (placeholder)
- [ ] Implement row click handlers for viewing details (placeholder)
- [ ] Add action buttons for Edit/Delete (placeholder functionality)
- [ ] Configure selection mode for grid rows
- [ ] Add basic keyboard navigation support

### Phase 5: Error Handling and Empty States
**Objective**: Implement proper UX for edge cases

#### Checklist:
- [ ] Show empty state message when no AgentDefinitions exist
- [ ] Display error messages for failed API calls
- [ ] Add retry mechanism for failed data loads
- [ ] Implement loading states during data operations
- [ ] Add user-friendly error notifications

### Phase 6: Styling and Polish
**Objective**: Apply the styling guidelines from UI.md

#### Checklist:
- [ ] Apply color scheme from UI.md (light gray background, blue primary buttons)
- [ ] Set consistent 4-space margins and padding
- [ ] Style grid headers and rows
- [ ] Add hover effects and interactive states
- [ ] Ensure responsive design for different screen sizes
- [ ] Test dark/light mode compatibility if applicable

## Technical Implementation Details

### Dependencies
All required Vaadin dependencies are already in pom.xml:
- `vaadin` (includes core components)
- `vaadin-spring-boot-starter` (Spring Boot integration)

### Package Structure
```
src/main/java/com/hdekker/ai_workflow/ui/
├── views/
│   └── AgentDefinitionListView.java
└── service/
    └── AgentDefinitionService.java
```

### Key Technical Considerations
1. **Endpoint Mapping**: Current REST endpoints use `/api/pipelines` and return `PipelineInfo` objects, not raw `AgentDefinition` objects
2. **Data Transformation**: Need to extract `AgentDefinition` from `PipelineInfo` responses
3. **Error Handling**: Implement proper Spring reactive error handling
4. **Performance**: Use lazy loading for large datasets if needed
5. **Security**: Ensure proper authentication/authorization if required

### Success Criteria
- [ ] Grid loads and displays AgentDefinition data from existing endpoints
- [ ] View is accessible at the root URL ""
- [ ] Grid is responsive and properly styled
- [ ] Error handling works for network/API failures
- [ ] Code follows project conventions (4-space indentation, Spring patterns)

## Next Steps After Implementation
1. Add creation form view (`/agents/new`)
2. Add edit form view (`/agents/{id}/edit`)
3. Add detail view (`/agents/{id}`)
4. Implement CRUD operations with proper state management
5. Add form validation and advanced features from UI.md

## Risk Mitigation
- **Risk**: PipelineController returns `PipelineInfo` not `AgentDefinition`
  - **Mitigation**: Investigate `PipelineInfo` structure and transform appropriately
- **Risk**: Existing agents may not have all required fields populated
  - **Mitigation**: Handle null/empty values gracefully in grid display
- **Risk**: Performance with large datasets
  - **Mitigation**: Implement pagination or lazy loading if needed during Phase 2