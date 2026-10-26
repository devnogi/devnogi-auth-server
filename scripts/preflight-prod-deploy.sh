#!/bin/sh
# Run from the staging directory before replacing the running service. Never print .env.prod.
set -eu

: "${IMAGE_NAME:?IMAGE_NAME is required}"
: "${IMAGE_REF:?IMAGE_REF is required}"
: "${DEPLOY_ENV_FILE:=.env.prod}"

expected_prefix="ghcr.io/${IMAGE_NAME}/auth-server@sha256:"
case "${IMAGE_REF}" in
  "${expected_prefix}"*) ;;
  *) echo "Deployment image must use the expected GHCR digest" >&2; exit 1 ;;
esac
digest=${IMAGE_REF#"${expected_prefix}"}
if ! printf '%s\n' "${digest}" | grep -Eq '^[0-9a-f]{64}$'; then
  echo "Deployment image digest is invalid" >&2
  exit 1
fi

if [ ! -f "${DEPLOY_ENV_FILE}" ] || [ ! -f docker-compose-auth-prod.yml ]; then
  echo "Deployment environment or Compose file is missing" >&2
  exit 1
fi

# These values are required by the packaged application.yml or the Compose file.
# Check names and non-empty values without sourcing or printing the secret file.
required_keys='SERVER_PORT BASE_URL DB_HOST DB_PORT DB_SCHEMA DB_USER DB_PASSWORD
GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET KAKAO_CLIENT_ID KAKAO_CLIENT_SECRET
NAVER_CLIENT_ID NAVER_CLIENT_SECRET MAIL_PASSWORD KAFKA_BOOTSTRAP_SERVERS
AWS_REGION AWS_ACCESS_KEY AWS_SECRET_KEY OCI_NAMESPACE JWT_SECRET_KEY
NICKNAME_TICKET_SECRET USER_VERIFICATION_HASH_SECRET MINIO_ENDPOINT'
for key in ${required_keys}; do
  if ! awk -v wanted="${key}" '
    /^[[:space:]]*(#|$)/ { next }
    {
      equals = index($0, "=")
      if (equals == 0) next
      name = substr($0, 1, equals - 1)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", name)
      if (name != wanted) next
      value = substr($0, equals + 1)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      count++
      if (value == "" || value == "\"\"" || value == "\047\047") empty = 1
    }
    END { exit !(count == 1 && !empty) }
  ' "${DEPLOY_ENV_FILE}"; then
    echo "Required deployment setting must appear exactly once with a non-empty value: ${key}" >&2
    exit 1
  fi
done

server_port=$(awk '
  {
    equals = index($0, "=")
    if (equals == 0) next
    name = substr($0, 1, equals - 1)
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", name)
    if (name != "SERVER_PORT") next
    value = substr($0, equals + 1)
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
    count++
  }
  END { if (count == 1) print value; else exit 1 }
' "${DEPLOY_ENV_FILE}") || {
  echo "SERVER_PORT must appear exactly once" >&2
  exit 1
}
case "${server_port}" in
  ''|*[!0-9]*) echo "SERVER_PORT must be numeric" >&2; exit 1 ;;
esac
if [ "${server_port}" -lt 1 ] || [ "${server_port}" -gt 65535 ]; then
  echo "SERVER_PORT is outside the TCP port range" >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the post-deployment health check" >&2
  exit 1
fi

if ! docker network inspect infra_devnogi-network >/dev/null 2>&1; then
  echo "Required Docker network is unavailable" >&2
  exit 1
fi

docker compose -p auth -f docker-compose-auth-prod.yml --env-file "${DEPLOY_ENV_FILE}" config --quiet
printf '%s\n' "${server_port}"
