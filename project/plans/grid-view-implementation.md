# Sub-Plan: Initial PipelineInfo Grid View Implementation

## Overview
Create an initial Vaadin Flow view that displays existing PipelineInfo objects in a grid layout, following the existing project structure and Vaadin configuration already present in the project. Since AgentDefinition is now embedded in PipelineInfo, we will display the complete pipeline information including both metadata and agent configuration.

## Current Project State Analysis
- ✅ Vaadin Flow 24.9.4 already configured in pom.xml
- ✅ AgentDefinition record exists at `src/main/java/com/hdekker/ai_workflow/pipeline/domain/AgentDefinition.java`
- ✅ PipelineInfo record now includes complete AgentDefinition object (refactored)
- ✅ PipelineRestController exists with endpoints for CRUD operations
- ✅ REST endpoints at `/api/pipelines` return PipelineInfo objects with embedded AgentDefinition
- ✅ Vaadin views and UI components created and functional

## Implementation Plan

### Phase 1: Basic View Structure Setup ✅ COMPLETED
**Objective**: Create basic Vaadin Flow view structure and routing

#### Checklist:
- [x] Create `PipelineInfoListView` class extending `VerticalLayout`
- [x] Create `views` package under `com.hdekker.ai_workflow.ui`
- [x] Configure main route with `@Route("")` for default landing page
- [x] Add basic view title and layout structure
- [x] Set up responsive layout with proper spacing

#### Files created:
- ✅ `src/main/java/com/hdekker/ai_workflow/ui/views/PipelineInfoListView.java`
- ✅ `src/main/frontend/themes/default/styles/styles.css`
- ✅ `src/test/java/com/hdekker/ai_workflow/ui/views/PipelineInfoListViewTest.java`
- ✅ `src/main/java/com/hdekker/ai_workflow/ui/service/PipelineInfoService.java`

### Phase 2: Grid Component Integration ✅ COMPLETED
**Objective**: Implement Vaadin Grid to display PipelineInfo objects

#### Checklist:
- [x] Add Vaadin Grid component to the view
- [x] Configure Grid columns for PipelineInfo fields:
  - Pipeline ID (String)
  - Agent Title (from AgentDefinition)
  - Agent Type (from AgentDefinition)
  - File Input Regex (from AgentDefinition)
  - Source (YAML/DYNAMIC)
  - Created At (LocalDateTime)
  - Active Status (Boolean)
- [x] Set up proper column widths and sorting
- [x] Add basic styling for the grid
- [x] Implement responsive grid behavior

#### Grid columns configuration:
```java
grid.addColumn(PipelineInfo::id).setHeader("ID").setAutoWidth(true);
grid.addColumn(pipeline -> pipeline.agentDefinition().title()).setHeader("Title").setAutoWidth(true);
grid.addColumn(pipeline -> pipeline.agentDefinition().agentType()).setHeader("Agent Type").setAutoWidth(true);
grid.addColumn(pipeline -> pipeline.agentDefinition().fileInputRegex()).setHeader("File Regex").setAutoWidth(true);
grid.addColumn(PipelineInfo::source).setHeader("Source").setAutoWidth(true);
grid.addColumn(PipelineInfo::createdAt).setHeader("Created").setAutoWidth(true);
```

### Phase 3: Data Integration ✅ COMPLETED
**Objective**: Connect grid to PipelineRestController endpoints

#### Checklist:
- [x] Create `PipelineInfoService` to handle REST API calls
- [x] Implement HTTP client using Spring's `WebClient` or `RestTemplate`
- [x] Map PipelineRestController endpoints to service methods:
  - `GET /api/pipelines` → `getAllPipelineInfos()`
  - `DELETE /api/pipelines/{id}` → `deletePipeline(String id)`
- [x] Handle error cases (empty response, network errors)
- [x] No transformation needed - PipelineInfo now contains complete AgentDefinition
- [x] Add loading indicators during data fetch
- [x] Implement automatic data refresh on view load

#### Service method mapping:
```java
// PipelineRestController returns List<PipelineInfo>
// PipelineInfo now contains complete AgentDefinition object
// No transformation needed - display PipelineInfo directly
```

#### Phase 3 Implementation Summary
**Completed**: Data integration successfully connects grid to PipelineRestController endpoints
- Created `PipelineInfoService` with WebClient for reactive HTTP calls
- Implemented `getAllPipelineInfos()` and `deletePipeline()` methods
- Added comprehensive error handling for network failures and HTTP errors
- Integrated loading indicators (ProgressBar) with proper UX during data operations
- Updated `PipelineInfoListView` with service dependency injection and reactive data loading
- Grid now automatically loads and displays real PipelineInfo data on view initialization
- All tests pass and compilation succeeds

### Phase 4: View Navigation and Basic Interactions
**Objective**: Add navigation capabilities and basic grid interactions

#### Checklist:
- [ ] Add "New Pipeline" button to navigate to creation form (placeholder)
- [ ] Implement row click handlers for viewing pipeline details including full AgentDefinition (placeholder)
- [ ] Add action buttons for Edit/Delete operations using PipelineInfo ID (placeholder functionality)
- [ ] Configure selection mode for grid rows
- [ ] Add basic keyboard navigation support
- [ ] Display pipeline source (YAML/DYNAMIC) with visual indicators

### Phase 5: Error Handling and Empty States
**Objective**: Implement proper UX for edge cases

#### Checklist:
- [ ] Show empty state message when no PipelineInfo objects exist
- [ ] Display error messages for failed API calls
- [ ] Add retry mechanism for failed data loads
- [ ] Implement loading states during data operations
- [ ] Add user-friendly error notifications
- [ ] Handle cases where AgentDefinition fields within PipelineInfo are null/empty

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
│   └── PipelineInfoListView.java
└── service/
    └── PipelineInfoService.java
```

### Key Technical Considerations
1. **Simplified Data Flow**: Current REST endpoints use `/api/pipelines` and return `PipelineInfo` objects with embedded `AgentDefinition`
2. **No Transformation Needed**: PipelineInfo now contains complete AgentDefinition, eliminating transformation logic
3. **Rich Display Options**: Can display both pipeline metadata (ID, source, createdAt) and agent configuration
4. **Error Handling**: Implement proper Spring reactive error handling
5. **Performance**: Use lazy loading for large datasets if needed
6. **Security**: Ensure proper authentication/authorization if required

### Success Criteria
- [x] Grid loads and displays PipelineInfo data from existing endpoints
- [x] View is accessible at the root URL ""
- [x] Grid displays both pipeline metadata and agent definition fields
- [x] Grid is responsive and properly styled
- [x] Error handling works for network/API failures
- [x] Code follows project conventions (4-space indentation, Spring patterns)

## Next Steps After Implementation
1. Add creation form view (`/pipelines/new`) that creates AgentDefinition and submits to PipelineRestController
2. Add edit form view (`/pipelines/{id}/edit`) that updates AgentDefinition within existing PipelineInfo
3. Add detail view (`/pipelines/{id}`) that displays complete PipelineInfo including AgentDefinition
4. Implement CRUD operations with proper state management using PipelineInfo as the primary entity
5. Add form validation and advanced features from UI.md working with AgentDefinition fields
6. Implement source-specific behavior (YAML pipelines may be read-only)

## Risk Mitigation
- **Risk**: PipelineInfo structure may have null/empty AgentDefinition fields
  - **Mitigation**: Handle null/empty values gracefully in grid display with fallback values
- **Risk**: JSON serialization of nested PipelineInfo objects
  - **Mitigation**: Test JSON serialization/deserialization thoroughly (Jackson should handle nested records properly)
- **Risk**: Performance with large datasets
  - **Mitigation**: Implement pagination or lazy loading if needed during Phase 2
- **Risk**: Mixed data sources (YAML vs DYNAMIC pipelines) may have different field completeness
  - **Mitigation**: Use conditional rendering in grid based on source type