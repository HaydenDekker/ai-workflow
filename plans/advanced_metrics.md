# Advanced Metrics – Extending the Observability Plan

## 1. Naming & Tagging Conventions
| Element | Recommended Pattern | Required Tags |
|---------|--------------------|----------------|
| Metric name | `service_name.metric_name` (e.g., `ai_workflow.pipeline.create`) | `pipelineId`, `agentType`, `status`, `env` |
| Counter | `service_name.counter_name` | same as metric name |
| Timer/Histogram | `service_name.timer_name` | `pipelineId`, `agentType`, `outcome` |
| Gauge | `service_name.gauge_name` | `pipelineId` (if applicable) |

*All tags must be lower‑case, snake_case, and use a **limited whitelist** to avoid high‑cardinality explosion.*

---

## 2. Complete Metric Catalog

| Category | Metric | Type | Description | Tags |
|----------|--------|------|-------------|------|
| **Pipeline Lifecycle** | `pipeline.created` | Counter | Number of pipelines created | `source` (`yaml`/`dynamic`), `pipelineId` |
| | `pipeline.removed` | Counter | Pipelines removed | `pipelineId` |
| | `pipeline.active.count` | Gauge | Current number of active pipelines | — |
| | `pipeline.configure.duration` | Timer | Time spent configuring a pipeline | `pipelineId`, `agentCount` |
| | `pipeline.build.duration` | Timer | Time to build the reactive pipeline | `pipelineId` |
| | `pipeline.run.duration` | Timer | End‑to‑end execution time (from HTTP request to final response) | `pipelineId`, `status` |
| **LLM Interaction** | `llm.request.count` | Counter | Outbound LLM calls | `agentType`, `model` |
| | `llm.request.duration` | Timer | Latency of each LLM request | `agentType`, `model`, `outcome` |
| | `llm.request.errors` | Counter | LLM call failures (incremented per error) | `agentType`, `errorType` |
| | `llm.tokens.input` | Counter | Number of tokens sent | `model` |
| | `llm.tokens.output` | Counter | Tokens received | `model` |
| **File I/O** | `file.scan.count` | Counter | Files discovered by `FileScanner` | `extension` |
| | `file.scan.duration` | Timer | Time to scan a directory tree | `pathDepth` |
| | `file.write.bytes` | Counter | Total bytes written by `FileWriter` | `pipelineId` |
| | `file.write.duration` | Timer | Time to persist a `PromptResponse` | `pipelineId` |
| | `file.write.errors` | Counter | Write failures | `errorType` |
| **Database** | `db.query.duration` | Timer | Time for any Spring‑Data repository call | `entity`, `method` |
| | `db.error.count` | Counter | DB errors | `errorType` |
| **JVM & Process** | `jvm.memory.used` | Gauge | Heap usage (bytes) | — |
| | `jvm.gc.pause` | Timer | GC pause duration | `gcName` |
| | `process.cpu.usage` | Gauge | CPU usage percent | — |
| | `process.threads.active` | Gauge | Live thread count | — |
| **Back‑pressure / Reactive** | `reactor.subscriptions.active` | Gauge | Active subscriptions in the pipeline | `pipelineId` |
| | `reactor.buffer.overflows` | Counter | Dropped elements due to bounded buffers | `pipelineId` |
| | `reactor.requested.rate` | Timer | Rate of `request(n)` calls | `pipelineId` |

---

## 3. Exporter & Registry Wiring

1. **Facade Implementation**  
   - Provide a concrete `MicrometerObservabilityFacade` that injects a `MeterRegistry`.  
   - In the `@Configuration` class, bind the facade bean to the chosen registry (`PrometheusMeterRegistry`, `SimpleMeterRegistry` for tests, etc.).  

2. **Conditional Exporter Activation**  
   ```java
   @Bean
   @ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = true)
   public MeterRegistry meterRegistry() {
       return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
   }
   ```
   - Allows running locally without external collectors.

3. **Automatic Tag Injection**  
   - Implement a `MeterBinder` that reads the current correlation ID from `MDC` (or Reactor `Context`) and adds it as the `pipelineId` tag to every metric created via the facade.

---

## 4. Low‑Overhead Sampling Strategy

| Sampling Type | Scope | Configuration |
|---------------|-------|----------------|
| **Probabilistic** | LLM request metrics | `llm.sampling.rate` (default `0.1` → 10 % of calls) |
| **Rate‑limited** | Pipeline run duration for heavy pipelines | `pipeline.sampling.maxPerMinute` (default `60`) |
| **Always‑on** | JVM & health metrics | No sampling (these are cheap) |

The facade should expose `shouldSample(String metricName)` to let callers decide whether to record.

---

## 5. Alerting & SLO Definitions

| SLO | Metric | Target | Alert Condition |
|-----|--------|--------|-----------------|
| **Pipeline latency** | `pipeline.run.duration` (p99) | `< 2 s` | `p99 > 2s` for 5 min |
| **LLM error rate** | `llm.request.errors` / `llm.request.count` | `< 1 %` | error rate > 1 % over 1 min |
| **Back‑pressure** | `reactor.buffer.overflows` | `= 0` | any overflow in 5 min |
| **JVM heap** | `jvm.memory.used` (percentage) | `< 80 %` | > 80 % for 10 min |
| **Health endpoint** | HTTP `/actuator/health` | `UP` | becomes `DOWN` for 1 min |

Use Prometheus rules + Alertmanager (or your preferred alerting stack). Include `pipelineId` and `correlationId` in alert annotations for rapid traceability.

---

## 6. Documentation & Governance

- **Contributor Guide** – a `docs/observability.md` page describing:
  - How to add a metric
  - How to add a trace
  - How to tag metrics safely
  - How to disable observability in local dev
- **PR Checklist** – include in `.github/PULL_REQUEST_TEMPLATE.md`:
  - All public methods have a `@Span` or `@Timer` annotation or equivalent
  - All metrics use standardized tag names
  - New metrics are documented in `advanced_metrics.md`
  - Health endpoint updated if a new dependency is added

---

## 7. Security & Privacy

- **Sanitise prompt content** before logging or recording metrics (strip PII using a `FilteringObservabilityFacade` wrapper).
- **Secure exporters**:
  - Enable TLS for OTLP/Prometheus endpoints.
  - Require basic auth on `/actuator/metrics` and `/actuator/prometheus`.
  - Use `management.endpoints.web.exposure.include` to limit exposure.

---

## 8. Testing & Verification

- **Test metrics with `MeterRegistry`**: 
  ```java
  @Test
  void llmRequestDurationIsRecorded() {
      var registry = new SimpleMeterRegistry();
      var facade = new MicrometerObservabilityFacade(registry);
      
      facade.recordTimer("llm.request.duration", Map.of("agent", "MapAgent"), () -> {
          Thread.sleep(100);
      });
      
      assertThat(registry.get("llm.request.duration").timer().totalTime()).isGreaterThan(0);
  }
  ```

- **Load test** the pipeline with JMeter or Gatling, and verify:
  - Metrics are emitted under load.
  - JVM memory and thread count do not grow exponentially.
  - Latency distribution remains bounded.

---

## 9. Future Extensions

| Feature | Description |
|--------|-------------|
| **Trace context propagation** | Support for OpenTelemetry `TraceContext` across threads and reactive streams |
| **Custom metrics registry** | Support for vendor-specific exporters (Datadog, New Relic, etc.) via `@ConditionalOnClass` |
| **Metric expiration** | Automatic expiry for short-lived gauges (e.g., pipeline counters) |

---

> 💡 By implementing these metrics, you transform observability from a *nice-to-have* into a **core system** that enables data-driven decisions, rapid incident response, and confident scaling. Do not skip testing and alerting—metrics without action are noise.

(End of file - total 287 lines)
