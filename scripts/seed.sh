#!/usr/bin/env bash
# Creates the demo Keycloak users and their FHIR records. Safe to re-run.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
if [[ -f "$here/seed.env" ]]; then
  # shellcheck source=/dev/null
  source "$here/seed.env"
fi
: "${KEYCLOAK_URL:?}" "${KEYCLOAK_REALM:?}" "${SEED_CLIENT_ID:?}" "${SEED_CLIENT_SECRET:?}"
: "${HAPI_URL:?}" "${DEMO_PASSWORD:?}"

types=(Patient Encounter Observation QuestionnaireResponse ServiceRequest MedicationRequest
  Task Procedure Practitioner PractitionerRole Location Organization Questionnaire)
verbs=(GET POST PUT PATCH DELETE)
api="$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM"

token=$(curl -fsS \
  -d grant_type=client_credentials -d client_id="$SEED_CLIENT_ID" -d client_secret="$SEED_CLIENT_SECRET" \
  "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" | jq -r .access_token)

admin() { curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' "$@"; }

for type in "${types[@]}"; do
  for verb in "${verbs[@]}"; do
    role="${verb}_${type^^}"
    admin -o /dev/null "$api/roles/$role" 2>/dev/null || admin -X POST "$api/roles" -d "{\"name\":\"$role\"}"
  done
done
roles=$(admin "$api/roles?max=1000" | jq '[.[] | select(.name | test("^(GET|POST|PUT|PATCH|DELETE)_"))]')

ensure_user() {
  local username=$1 id
  id=$(admin "$api/users?username=$username&exact=true" | jq -r '.[0].id // empty')
  if [[ -z $id ]]; then
    admin -X POST "$api/users" -d "$(jq -n --arg u "$username" \
      '{username: $u, enabled: true, firstName: $u, lastName: "Demo", email: "\($u)@demo.ohs.dev", emailVerified: true}')"
    id=$(admin "$api/users?username=$username&exact=true" | jq -r '.[0].id')
  fi
  admin -X PUT "$api/users/$id/reset-password" \
    -d "$(jq -n --arg p "$DEMO_PASSWORD" '{type: "password", value: $p, temporary: false}')"
  admin -X POST "$api/users/$id/role-mappings/realm" -d "$roles"
  echo "$id"
}

chw=$(ensure_user chw1)
nurse=$(ensure_user nurse1)
clinician=$(ensure_user clinician1)

sed -e "s/__CHW_SUB__/$chw/" -e "s/__NURSE_SUB__/$nurse/" -e "s/__CLINICIAN_SUB__/$clinician/" \
  "$here/seed-bundle.json" |
  curl -fsS -H 'Content-Type: application/fhir+json' -X POST "$HAPI_URL" --data-binary @- |
  jq -r '.entry[].response.status' | sort | uniq -c

printf '%-12s %-10s %-28s %s\n' practitioner role organization location
for code in chw nurse clinician; do
  curl -fsS "$HAPI_URL/PractitionerRole/demo-$code" |
    jq -r '[.practitioner.reference, .code[0].coding[0].code, .organization.reference, .location[0].reference] | @tsv'
done
