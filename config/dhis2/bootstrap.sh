#!/bin/sh
#
# Seeds an empty DHIS2 instance with the minimal metadata this reference implementation needs:
# one org unit, a tracked entity type, six attributes and one Tracker program
# (see metadata.json), then puts the admin user in the org unit's capture + search scope.
#
# Idempotent: safe to run against an instance that has already been bootstrapped.
#
set -eu

DHIS2_URL="${DHIS2_URL:-http://localhost:8080}"
DHIS2_AUTH="${DHIS2_AUTH:-admin:district}"
METADATA_FILE="${METADATA_FILE:-$(dirname "$0")/metadata.json}"
ORG_UNIT_ID="DemoClinic1"

echo "Waiting for DHIS2 at ${DHIS2_URL} ..."
until curl -fsS -o /dev/null -u "${DHIS2_AUTH}" "${DHIS2_URL}/api/system/info"; do sleep 5; done

echo "Importing metadata ..."
REPORT=$(curl -fsS -u "${DHIS2_AUTH}" -H 'Content-Type: application/json' \
  --data-binary "@${METADATA_FILE}" \
  "${DHIS2_URL}/api/metadata?importStrategy=CREATE_AND_UPDATE&atomicMode=ALL")
echo "${REPORT}" | grep -q '"status":"OK"' || { echo "Metadata import failed: ${REPORT}"; exit 1; }

ADMIN_ID=$(curl -fsS -u "${DHIS2_AUTH}" "${DHIS2_URL}/api/me?fields=id" | sed -E 's/.*"id":"([^"]+)".*/\1/')
echo "Assigning org unit ${ORG_UNIT_ID} to user ${ADMIN_ID} (capture + search scope) ..."
for collection in organisationUnits teiSearchOrganisationUnits; do
  curl -fsS -o /dev/null -u "${DHIS2_AUTH}" -X POST \
    "${DHIS2_URL}/api/users/${ADMIN_ID}/${collection}/${ORG_UNIT_ID}" || true
done

echo "DHIS2 bootstrap complete."
