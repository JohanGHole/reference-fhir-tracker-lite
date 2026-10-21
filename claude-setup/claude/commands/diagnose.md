---
description: Inspect the running integration with the Camel MCP runtime tools
argument-hint: [symptom, e.g. "last POST returned 502"]
---
The integration is running locally (`./mvnw spring-boot:run`). Investigate: $ARGUMENTS

1. `camel_runtime_processes` to find the app (name `fhir-tracker-lite`), then
   `camel_runtime_routes` for route state and failure counts.
2. `camel_runtime_history` for the last exchange (route path, headers, body per step) and
   `camel_runtime_errors` for recent failures.
3. If there is a stack trace, run it through `camel_error_diagnose` (pinned version).
4. Explain what happened step by step, point at the route step (by `id`/`description`) or
   mapping line responsible, and propose a fix plus the test that would have caught it.
   Do not change files until I confirm.
