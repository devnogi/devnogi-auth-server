#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/bin"
cp "$repository_root/docker-compose-auth-prod.yml" "$test_dir/"
cp "$repository_root/scripts/preflight-prod-deploy.sh" "$test_dir/"
cp "$repository_root/.env.example" "$test_dir/base.env"
printf '%s\n' 'MINIO_ENDPOINT=http://example.invalid' >> "$test_dir/base.env"
# Convert the HTTP localhost fixture to a synthetic production cookie scope.
sed 's/^JWT_COOKIE_DOMAIN=.*/JWT_COOKIE_DOMAIN=.memonogi.com/;
     s/^JWT_COOKIE_SECURE=.*/JWT_COOKIE_SECURE=true/;
     s/^JWT_COOKIE_SAME_SITE=.*/JWT_COOKIE_SAME_SITE=Lax/' \
  "$test_dir/base.env" > "$test_dir/prod.env"
cat > "$test_dir/bin/docker" <<'EOF'
#!/bin/sh
exit 0
EOF
chmod +x "$test_dir/bin/docker"

export PATH="$test_dir/bin:$PATH"
export IMAGE_NAME='devnogi/devnogi-auth-server'
export IMAGE_REF="ghcr.io/${IMAGE_NAME}/auth-server@sha256:$(printf '%064d' 0)"
export DEPLOY_ENV_FILE='.env.prod'
cd "$test_dir"

cp prod.env .env.prod
test "$(sh preflight-prod-deploy.sh)" = '8091'

expect_rejected() {
  if sh preflight-prod-deploy.sh >/dev/null 2>&1; then
    echo "Preflight accepted invalid input: $1" >&2
    exit 1
  fi
}

cp prod.env .env.prod
printf '%s\n' 'DB_PASSWORD=duplicate' >> .env.prod
expect_rejected 'duplicate required key'

sed 's/^DB_PASSWORD=.*/DB_PASSWORD=/' prod.env > .env.prod
expect_rejected 'empty required key'

sed 's/^DB_PASSWORD=.*/DB_PASSWORD=""/' prod.env > .env.prod
expect_rejected 'quoted empty required key'

sed '/^DB_PASSWORD=/d' prod.env > .env.prod
expect_rejected 'missing required key'

reject_setting() {
  key=$1
  value=$2
  description=$3
  sed "s/^${key}=.*/${key}=${value}/" prod.env > .env.prod
  expect_rejected "$description"
}

for key in JWT_ACCESS_TOKEN_VALIDITY JWT_REFRESH_TOKEN_VALIDITY \
           JWT_COOKIE_DOMAIN JWT_COOKIE_SECURE JWT_COOKIE_SAME_SITE; do
  sed "/^${key}=/d" prod.env > .env.prod
  expect_rejected "missing $key"
  cp prod.env .env.prod
  printf '%s\n' "${key}=duplicate" >> .env.prod
  expect_rejected "duplicate $key"
done

reject_setting JWT_ACCESS_TOKEN_VALIDITY 3600 'legacy access default in milliseconds'
reject_setting JWT_REFRESH_TOKEN_VALIDITY 86400 'legacy refresh default in milliseconds'
reject_setting JWT_ACCESS_TOKEN_VALIDITY 0 'zero access lifetime'
reject_setting JWT_ACCESS_TOKEN_VALIDITY 86400001 'excessive access lifetime'
reject_setting JWT_ACCESS_TOKEN_VALIDITY invalid 'nonnumeric access lifetime'
reject_setting JWT_REFRESH_TOKEN_VALIDITY 3600000 'refresh equals access'
reject_setting JWT_REFRESH_TOKEN_VALIDITY 60000 'refresh shorter than access'
reject_setting JWT_REFRESH_TOKEN_VALIDITY 2592000001 'excessive refresh lifetime'
reject_setting JWT_REFRESH_TOKEN_VALIDITY invalid 'nonnumeric refresh lifetime'
reject_setting JWT_COOKIE_DOMAIN '' 'empty cookie domain'
reject_setting JWT_COOKIE_DOMAIN evil.invalid 'foreign cookie domain'
reject_setting JWT_COOKIE_DOMAIN api.memonogi.com 'host-specific cookie domain'
reject_setting JWT_COOKIE_SECURE false 'insecure cookie'
reject_setting JWT_COOKIE_SAME_SITE None 'cross-site cookie policy'
reject_setting JWT_COOKIE_SAME_SITE invalid 'invalid cookie policy'

sed 's/^JWT_COOKIE_DOMAIN=.*/JWT_COOKIE_DOMAIN=memonogi.com/' prod.env > .env.prod
test "$(sh preflight-prod-deploy.sh)" = '8091'

echo 'Preflight synthetic cases passed'
