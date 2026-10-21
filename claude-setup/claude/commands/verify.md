---
description: Pre-PR check — schema-validate routes, run tests, dependency and hardening review
---
Run the pre-PR checks from CLAUDE.md and report a short pass/fail table:

1. `camel_validate_yaml_dsl` on every file in `iol/src/main/resources/camel/`.
2. Tier 1 and tier 2 tests: `./mvnw -q test` in `iol/`.
3. `camel_dependency_check` with `iol/pom.xml` and all route files (pinned version from CLAUDE.md).
4. `camel_route_harden_context` on each route file. Turn the output into concrete,
   repo-specific recommendations (e.g. which `application.yaml` keys should come from env vars);
   do not apply them without asking.
5. Ask whether to run tier 3 (`./mvnw verify -Pfunctional`, needs Docker).
