# CLAUDE.md

Guidance for Claude Code when working in this repository.

## General guidelines
* Avoid verbose comments in source code. Add short, descriptive comments for code that is not self-documenting. 
* Follow the same general structure as the DHIS2 reference implementations: 
   * https://github.com/dhis2/reference-civil-registry-lookup
   * https://github.com/dhis2/reference-dhis2-tracker-lab-result-integration
   * https://github.com/dhis2/reference-org-unit-sync
   * https://github.com/dhis2/reference-dhis2-mosip-integration

## What this is

A deliberately small DHIS2 reference implementation: a Spring Boot + Camel YAML DSL integration
("IOL", in `iol/`) that receives a FHIR R4 `Patient` on `POST /api/Patient`, maps it with
DataSonnet, and registers it in DHIS2 Tracker as a tracked entity enrolled in a program. It
follows the same layout as `dhis2/reference-dhis2-tracker-lab-result-integration`.

```
iol/src/main/resources/camel/*.camel.yaml     routes (YAML DSL)
iol/src/main/resources/datasonnet/*.ds        mappings
iol/src/main/resources/application.yaml       all DHIS2 UIDs and endpoints (never hard-code UIDs in routes)
iol/src/main/java/...                         beans only (Dhis2Client, error processor)
config/dhis2/metadata.json + bootstrap.sh     the DHIS2 program this IOL targets
data/fhir/*.json                              sample payloads for curl
```

## Version pinning (read this first)

The project pins **Camel 4.21.0 on Spring Boot** (`iol/pom.xml`). The Camel MCP server's catalog
defaults to whatever it ships with (newer), so **always pass the version explicitly** on every
catalog/validation call:

- `runtime`: `spring-boot`
- `camelVersion`: `4.21.0`
- `platformBom`: `org.apache.camel.springboot:camel-spring-boot-bom:4.21.0` (pass the GAV, an empty string is rejected)

If `iol/pom.xml` changes `camel.version`, update this section in the same change.

## Workflow rules

1. **Look it up, don't recall it.** Before writing or changing any endpoint URI, EIP, expression
   language function, or data format option, fetch the docs from the Camel MCP server
   (`camel_catalog_component_doc`, `camel_catalog_eip_doc`, `camel_catalog_language_doc`,
   `camel_catalog_dataformat_doc`) at the pinned version. API-style components (`dhis2`, `fhir`)
   put important options such as `client` in the *advanced* group: use `includeOptions: all`
   with an `optionsFilter` rather than assuming an option does not exist.
2. **Validate every YAML edit.** After editing a `*.camel.yaml` file, run `camel_validate_yaml_dsl`
   on the whole file, and `camel_validate_route` on any new or changed endpoint URI. The YAML schema
   requires `constant:` values to be strings, so quote numbers and booleans (`constant: "201"`).
3. **Test in tiers, fastest first** (from `iol/`):
   - Tier 1, mapping only, no Spring: `./mvnw -q test -Dtest=PatientToTrackerDataSonnetTestCase`
   - Tier 2, full app against an in-process fake DHIS2, no Docker: `./mvnw -q test`
   - Tier 3, real DHIS2 via Testcontainers (Docker, a few minutes): `./mvnw verify -Pfunctional`
   Run tier 1 after every `.ds` change and tier 2 before saying a route change is done. Mention
   explicitly if tier 3 was not run.
4. **New route → new test.** When adding a route, generate a starting point with
   `camel_route_test_scaffold` (`runtime: spring-boot`), then adapt it to this repo's conventions:
   fake external systems with a Jetty-backed Camel route (see `ReceivePatientRouteTestCase`), keep
   fixtures in `src/test/resources/{fhir,dhis2,expected}`, assert on the payload DHIS2 receives.
5. **Debug the running app with runtime tools, not log scraping.** With the app running
   (`./mvnw spring-boot:run` in `iol/`), use `camel_runtime_routes`, `camel_runtime_history`
   (last exchange, always captured), `camel_runtime_errors`, and `camel_runtime_trace`. Feed
   stack traces to `camel_error_diagnose`. The app is visible to these tools because of the
   `camel-cli-connector-starter` dependency; it must run on the host, not in Docker.
6. **Before a PR**: `camel_route_harden_context` on each route file and `camel_dependency_check`
   with `iol/pom.xml` plus the routes. Report findings; don't silently bump versions.

## Conventions (shared with the other DHIS2 reference implementations)

- Every step gets a `description`; producer steps that tests may replace get an `id`
  (e.g. `dhis2TrackerImport`) so `AdviceWith.weaveById` works.
- Talk to DHIS2 only through the `dhis2:` component with `client: "#dhis2Client"`; synchronous
  tracker imports use `path: tracker?async=false`.
- Errors returned to callers are FHIR `OperationOutcome`s built by `direct:operationOutcome`
  from the `issueCode` and `diagnostics` variables.
- DataSonnet reads configuration with `cml.properties('...')` and per-exchange values with
  `cml.exchangeProperty('...')` / `cml.variable('...')`.
- Java and YAML files carry the BSD-3 header used across `dhis2/reference-*` repos.

## Known MCP server quirks

- `camel_version_list` currently fails with a NullPointerException (`mavenResolver` is null).
  Read the version from `iol/pom.xml` instead.
- Runtime tools auto-detect the process only when exactly one Camel app is running; otherwise
  call `camel_runtime_processes` and pass `nameOrPid` (`fhir-tracker-lite`).
