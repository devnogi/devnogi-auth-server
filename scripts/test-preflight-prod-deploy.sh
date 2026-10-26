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

cp base.env .env.prod
test "$(sh preflight-prod-deploy.sh)" = '8091'

expect_rejected() {
  if sh preflight-prod-deploy.sh >/dev/null 2>&1; then
    echo "Preflight accepted invalid input: $1" >&2
    exit 1
  fi
}

cp base.env .env.prod
printf '%s\n' 'DB_PASSWORD=duplicate' >> .env.prod
expect_rejected 'duplicate required key'

sed 's/^DB_PASSWORD=.*/DB_PASSWORD=/' base.env > .env.prod
expect_rejected 'empty required key'

sed 's/^DB_PASSWORD=.*/DB_PASSWORD=""/' base.env > .env.prod
expect_rejected 'quoted empty required key'

sed '/^DB_PASSWORD=/d' base.env > .env.prod
expect_rejected 'missing required key'

echo 'Preflight synthetic cases passed'
