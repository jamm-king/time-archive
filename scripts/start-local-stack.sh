#!/usr/bin/env bash

set -euo pipefail

MAX_ATTEMPTS="${COMPOSE_START_MAX_ATTEMPTS:-3}"
RETRY_DELAY_SECONDS="${COMPOSE_START_RETRY_DELAY_SECONDS:-5}"

if [[ ! "$MAX_ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "[start-local-stack] COMPOSE_START_MAX_ATTEMPTS must be a positive integer" >&2
  exit 2
fi

if [[ ! "$RETRY_DELAY_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "[start-local-stack] COMPOSE_START_RETRY_DELAY_SECONDS must be a non-negative integer" >&2
  exit 2
fi

for (( attempt = 1; attempt <= MAX_ATTEMPTS; attempt++ )); do
  echo "[start-local-stack] Starting Compose stack (attempt $attempt/$MAX_ATTEMPTS)"

  if docker compose "$@"; then
    echo "[start-local-stack] Compose stack started"
    exit 0
  else
    status=$?
  fi

  if (( attempt == MAX_ATTEMPTS )); then
    echo "[start-local-stack] Compose stack failed after $MAX_ATTEMPTS attempts" >&2
    exit "$status"
  fi

  sleep "$(( RETRY_DELAY_SECONDS * attempt ))"
done
