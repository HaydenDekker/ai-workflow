# AI Workflow

Aiming to have AI act as the navigator rather than the driver of pair programming. A root directory is monitored for file events. The file is pushed into the configured prompt pipeline and a response is provided. The responses are stored back to the file system and trigger further events for any listening agent. Agents listen to file change events and filter for relevance using a regex.

A few benefits to file first graph nodes: Outputting results as files, improves the git workflow, allowing state to be progressively captured and provides easy access the output by the user. Accessing is simply a matter of pulling up the directory and flicking through to see results.

I imagine working away, being creative, and the machine churning away in the background validating all of my work. I'm the expert, not the AI.

Update: 11-04-2026

  - Refactor terminology toward agents from pipeline
  - Add sqlite support
  - Add qdrant support
  - Add opencode agents
  - Add memory advisors

## Prompt Graphs

Building up to an agent,

- (done) dynamic configuration of prompt chain.

- (done) Response should include input pre-pended in a single string. (simplest approach, otherwise could enrich using message headers at beginning of prompt)

- (done) Prompt Graph Pipeline Builder, to accept PromptRequest and return PromptResponse. Requires mapping stages but is clearer on the intent of each stage.

## File Inputs

The database is used to capture file hash's. This assists at startup ensuring the entire graph is not triggered.

- (done) file scanner to listen to all file event types
- (done) Update pipeline configuration to filter on file regex.
- (done) Normalise windows paths in filename so input regex can use just unix path '/'
- (done) Update input file regex to accommodate extension where file/filename.txt would be path=file, name=filename and ext=txt.

## File Outputs

- (done) Create PromptResponseFileSystemAdapter to receive and store prompt responses.
- (done) Create FileOutput stage for prompt chain builder and enable configuration to define target file type.
- (done) Update pipeline to output document to the prompt file template where the file name is a function of the input file url.
- Outputs for conditional logic, how to define, parse and execute branches. Potentially a variable in the template that the LLM produces. Simply creates that branch file.

## Agent types

### Transform / Map

- (done) take input pipe to LLM and save output

### Fan Out / Splitter / List

- (done) Configuration allows agent type fan-out. The user needs to be able to designate and edge that should fan out into many documents.
- (done) Configuration allows outputFilenameTemplate variable of ${itemKey}, this allows the agent to extract the variable provided by the llm and insert it into the output file name essentially splitting the response.
- (done) Prompt provides 0,1,n of responses.

### Aggregation / Reduce

 - List of outputs aggregated to single prompt, a map().reduce() operation. Requires local state and feedback to ensure the prompt output is ready for the next response. For the api, the stage to select this is in the enrich phase, setting a flag for reduce. The domain model would need to carry this so that the prompt stage will cache the previous response for input. Surely this is ok.
 
### Tool Call

All agents can utilise tools to attend to their query.
 
 - Tool call - file read, allow agent to read from filesystem.

### Multimodal

- images and audio files, secondary filter to ensure file type can be provided to LLMAdpater.

## Pipeline Agent Configuration

The configuration can be imagined as a flattened graph where each node n1 and n2 are files in and out. The edge or path is the agent/prompt runner.

- (done) prompt configuration into file-system (allows easy design with .md)
- dynamic root folder monitor for agent configuration.
- (done) multiple chains in configuration

### Startup

- set a flag for autocopy of prepackaged prompts.

## Pipeline Control

- Trigger a chain to re-run manually. Anywhere along the chain should run it downstream.
- When should a graph state be invalidated? Change of file (yes), change of prompt (yes), 
  but only have to redo the invalidated steps, easier to redo the whole step initially.
- need a way to clear memeory of any prompts to reset the system. Involves clearing file-system hash cache.
