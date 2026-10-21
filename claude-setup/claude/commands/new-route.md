---
description: Add or change a Camel route end-to-end (docs → YAML → validate → test)
argument-hint: <what the route should do>
---
Implement this change to the integration: $ARGUMENTS

Follow CLAUDE.md. In order, and show me a short plan before editing:

1. Read the existing routes in `iol/src/main/resources/camel/` and the mapping in
   `iol/src/main/resources/datasonnet/` so the change fits the current flow.
2. For every component, EIP or language function you will use, fetch its docs from the Camel MCP
   server at the pinned version. Say which options you are relying on.
3. Make the edits (routes, DataSonnet, `application.yaml`, `config/dhis2/metadata.json` if DHIS2
   metadata is needed). No hard-coded UIDs in routes.
4. Run `camel_validate_yaml_dsl` on every changed route file and fix all errors.
5. Add or extend tests: tier 1 for mapping changes, tier 2 for route behaviour. For a brand-new
   route, start from `camel_route_test_scaffold` and adapt it to the repo's fake-DHIS2 pattern.
6. Run tier 1 then tier 2 (`./mvnw -q test` in `iol/`). Iterate until green.
7. Summarise: files changed, MCP lookups that shaped the code, tests added, and whether tier 3
   still needs to run.
