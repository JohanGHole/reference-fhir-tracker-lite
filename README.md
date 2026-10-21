# Reference FHIR Patient → DHIS2 Tracker (lite)

A deliberately small DHIS2 reference implementation that receives a FHIR R4 `Patient` and
registers it in DHIS2 Tracker. It uses the same stack and layout as
[reference-dhis2-tracker-lab-result-integration](https://github.com/dhis2/reference-dhis2-tracker-lab-result-integration)
(Spring Boot, Camel YAML DSL, DataSonnet, `camel-dhis2`, Testcontainers), cut down to one route so
it can be read in five minutes and extended live.

It doubles as a demo of **AI-assisted middleware development with Claude Code and the
[Camel MCP server](https://camel.apache.org/manual/camel-jbang-mcp.html)**. See
[`docs/DEMO.md`](docs/DEMO.md) for the run sheet and [`CLAUDE.md`](CLAUDE.md) for the workflow rules
Claude follows in this repo.

## Flow

```
FHIR client ──POST /api/Patient──▶ IOL (Camel)                                  DHIS2
                                   1. parse + check resource type (HAPI FHIR)
                                   2. require national ID identifier
                                   3. DataSonnet: Patient → tracker payload
                                   4. dhis2:post/resource tracker?async=false ──▶ /api/tracker
                    ◀── 201 {trackedEntity} | 4xx/502 OperationOutcome ─────────
```

| FHIR `Patient`                                         | DHIS2 attribute (program `DemoProgrm1`) |
|--------------------------------------------------------|-----------------------------------------|
| `identifier[system=urn:oid:2.16.578.1.12.4.1.4.1].value` | National ID (`AttrNatIdNo`, unique)     |
| `name[use=official]` (else first) `.given` joined       | Given name (`AttrGivenNm`)              |
| `name[...].family`                                     | Family name (`AttrFamilyN`)             |
| `gender` (male/female/other/unknown)                   | Sex (`AttrSexCode`, option set, same codes) |
| `birthDate`                                            | Date of birth (`AttrBirthDt`)           |
| `id`                                                   | FHIR Patient ID (`AttrFhirIdN`)         |

| Response | When |
|---|---|
| `201 {"trackedEntity": "...", "status": "OK"}` | Imported |
| `400 OperationOutcome` (`structure` / `invalid`) | Not parseable FHIR, or not a Patient |
| `422 OperationOutcome` (`required`) | No national ID identifier |
| `409 OperationOutcome` (`business-rule`) | DHIS2 rejected the import, e.g. duplicate national ID |
| `502 OperationOutcome` (`exception`) | DHIS2 unreachable or other DHIS2 error |

## Run it

Requirements: JDK 17+, Docker.

```bash
docker compose up -d                 # DHIS2 42 + Postgres; dhis2-bootstrap seeds metadata.json
docker compose logs -f dhis2-bootstrap   # wait for "DHIS2 bootstrap complete." (first boot takes a few minutes)

cd iol && ./mvnw spring-boot:run     # IOL on http://localhost:9080

curl -i -X POST http://localhost:9080/api/Patient \
  -H 'Content-Type: application/fhir+json' \
  --data @../data/fhir/patient-kari-nordmann.json
```

Open DHIS2 at http://localhost:8080 (admin / district) → Capture → *Demo Clinic* →
*FHIR Patient Registration (demo)* to see the registered person.

The IOL runs on the host rather than in Docker on purpose: the `camel-cli-connector-starter`
dependency makes it visible to the Camel MCP server's runtime tools, which only see local
processes. `iol/Dockerfile` is there for deployment.

## Tests

From `iol/`:

| Tier | Command | Needs | What it covers |
|---|---|---|---|
| 1 | `./mvnw test -Dtest=PatientToTrackerDataSonnetTestCase` | nothing | DataSonnet mapping against golden files |
| 2 | `./mvnw test` | nothing | Full app over HTTP against an in-process fake DHIS2; every error branch |
| 3 | `./mvnw verify -Pfunctional` | Docker | Real DHIS2 (Testcontainers) seeded from `config/dhis2/metadata.json`; duplicate rejection |

## Configuration

All DHIS2 UIDs, credentials and the national-ID identifier system live in
`iol/src/main/resources/application.yaml` and can be overridden the usual Spring Boot way
(e.g. `DHIS2_API_URL`, `DHIS2_API_PAT`). Use a personal access token rather than
username/password anywhere but a local demo.
