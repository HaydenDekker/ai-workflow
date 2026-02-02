---  
 Observability – Initial Setup (TDD‑Driven)
> **Goal:** Build a solid observability foundation (metrics, logs, tracing) that can later be extended with class‑by‑class recommendations.  
> **Approach:** For every new piece of infrastructure we first write a failing test, then implement just enough code to make it pass, and finally refactor if needed.  
---  
 ✅ Checklist
 1️⃣ Choose & Add Observability Stack  
- [ ] **Define test:** Verify that the build fails when the observability dependency is missing.  
- [ ] **Add dependency:** Choose one of the following (pick & add to `pom.xml`):  
  - Micrometer Core + Micrometer Registry Prometheus  
  - OpenTelemetry SDK + Exporter (Prometheus, Jaeger)  
- [ ] **Run test:** Ensure the new dependency resolves and the project compiles.  
 2️⃣ Expose Metrics Endpoint  
- [ ] **Define test:** Application starts but `/actuator/prometheus` (or `/metrics`) returns **404**.  
- [ ] **Implement:** Enable Spring Boot Actuator and the appropriate endpoint.  
- [ ] **Run test:** Endpoint now returns **200** with a non‑empty body.  
 3️⃣ Basic Application‑Level Metrics  
- [ ] **Define test:** Expect a `Timer` named `application.startup.time` to be registered after context load.  
- [ ] **Implement:** Register the timer in a `@Configuration` class.  
- [ ] **Run test:** Timer exists and records a non‑zero duration.  
 4️⃣ Structured Logging (JSON)  
- [ ] **Define test:** Log entry produced by a `@RestController` does **not** contain the required JSON fields (`timestamp`, `level`, `logger`, `traceId`).  
- [ ] **Implement:** Configure Logback (or Log4j2) with a JSON encoder.  
- [ ] **Run test:** Log entry now contains all required fields and is parsable as JSON.  
 5️⃣ Correlation IDs / MDC  
- [ ] **Define test:** No `traceId` is present in the MDC for a request.  
- [ ] **Implement:** Add a servlet filter (or Spring `HandlerInterceptor`) that injects a `traceId` into MDC.  
- [ ] **Run test:** Log lines for a request now include the same `traceId`.  
 6️⃣ Distributed Tracing  
- [ ] **Define test:** A sample controller method finishes without any spans exported.  
- [ ] **Implement:** Enable OpenTelemetry (or Spring Cloud Sleuth) and configure an exporter (Jaeger/Zipkin).  
- [ ] **Run test:** At least one `SERVER` span appears in the exporter.  
 7️⃣ Custom Class‑Level Metrics (foundation)  
- [ ] **Define test:** No metric exists for `com.example.service.MyService.process(...)`.  
- [ ] **Implement:** Add a `@Timed` (or manual `Timer`) annotation/bean around the method.  
- [ ] **Run test:** Metric `myservice_process_seconds` appears in the Prometheus scrape.  
 8️⃣ Health Checks  
- [ ] **Define test:** `/actuator/health` returns **DOWN** because metrics are not yet registered.  
- [ ] **Implement:** Add a custom `HealthIndicator` that validates metric registration.  
- [ ] **Run test:** Health endpoint now reports **UP**.  
 9️⃣ CI Integration  
- [ ] **Define test:** The CI pipeline runs `./mvnw verify` but fails when the observability tests are missing.  
- [ ] **Implement:** Add the new test classes to the main test source set and ensure they run in CI.  
- [ ] **Run test:** CI passes with all observability tests green.  
 🔟 Documentation & Onboarding  
- [ ] **Define test:** No README section exists describing how to view metrics/logs/traces locally.  
- [ ] **Implement:** Add a `docs/observability.md` with steps to run Prometheus, Grafana, Jaeger locally.  
- [ ] **Run test:** The documentation builds (e.g., via a Markdown linter) with no errors.  
---  
 How to Use This Checklist  
1. **Pick a checklist item** → write the failing test in `src/test/java/...`.  
2. **Run `./mvnw test`** – it should fail.  
3. **Add the minimal production code** (or config) needed.  
4. **Run the tests again** – they should now pass.  
5. **Commit** the test *and* the implementation together (TDD principle).  
When you finish an item, check the box. After the whole list is green you’ll have a robust observability foundation ready for the **Class‑by‑Class Observability Recommendations** section.
---