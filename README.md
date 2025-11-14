# AI Workflow

Aiming to have AI act as the navigator rather than the driver of pair programming.

## Prompt chains

Building up to an agent,

- (done) pipe a request through a configured LLM prompt and save to a database.

- (done) create a second request triggered off an initial request.

- (done) Move to dynamic configuration of prompt chain

- (done) Split prompt after persisting.

- (Cancelled - its simpler to adjust the prompt to provide just the filtered answers) Add filter after the split to stop optimise usage of GPU when no further processing is required.

- (done) Response should include input pre-pended in a single string. (simplest approach, otherwise could enrich using message headers at beginning of prompt)

- Messages, use spring message objects to pass content along the chain

### Prompt Response Splitter

- Prompt provides 0,1,n of responses

## Prompt Response View

1. (done) Display prompt responses.

2. (done) Show source file

3. Show prompt input for a response.

## Prompt Content Test

Need to work specific prompts to get them conistent accross various models. 
That can be acheived by adding additional prompt chains that trigger from
specific folder root where the test files can be placed in those roots and
the output of the chain viewed via the UI.

- dynamic root folder monitor configuration

- multiple chains in configuration

## TODO List

- List of outputs aggregated to single prompt. / but better being a map().reduce() operation.
- When should a prompt chain state be invalidated? Change of file (yes), change of prompt (yes), 
  but only have to redo the invalidated steps, easier to redo the whole step initially.