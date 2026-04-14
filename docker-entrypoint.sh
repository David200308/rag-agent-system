#!/bin/sh
# Read Docker secrets (files in /run/secrets/) and export them as environment
# variables before handing off to the main process.
#
# Convention: secret file "openai_api_key" → env var "OPENAI_API_KEY"
# An existing env var always takes precedence over the secret file, so you can
# still override individual values without touching the secrets directory.
set -e

if [ -d /run/secrets ]; then
  for secret_file in /run/secrets/*; do
    [ -f "$secret_file" ] || continue
    var=$(basename "$secret_file" | tr '[:lower:]' '[:upper:]')
    # Only set if the env var is not already present
    eval "existing=\${${var}:-}"
    if [ -z "$existing" ]; then
      export "${var}=$(cat "$secret_file")"
    fi
  done
fi

exec "$@"
