#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[bootstrap-host] %s\n' "$1"
}

fail() {
  printf '[bootstrap-host] ERROR: %s\n' "$1" >&2
  exit 1
}

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  fail "Run this script as root"
fi

if [[ ! -f /etc/os-release ]]; then
  fail "Cannot identify the operating system"
fi

# shellcheck disable=SC1091
source /etc/os-release
[[ "${ID:-}" == "amzn" && "${VERSION_ID:-}" == "2023" ]] ||
  fail "This bootstrap supports Amazon Linux 2023 only"

log "Installing required host packages"
dnf install -y docker python3

command -v aws >/dev/null 2>&1 ||
  fail "AWS CLI v2 must be installed by the approved AMI or CloudFormation bootstrap"
command -v amazon-cloudwatch-agent-ctl >/dev/null 2>&1 ||
  fail "CloudWatch Agent must be installed by CloudFormation before host bootstrap"

systemctl enable --now docker
systemctl enable --now amazon-ssm-agent

docker compose version >/dev/null 2>&1 ||
  fail "Docker Compose plugin must be installed at an approved pinned version"

install -d -m 750 /opt/time-archive
install -d -m 750 /opt/time-archive/deploy
install -d -m 700 /var/lib/time-archive
install -d -m 700 /var/lib/time-archive/deployments
install -d -m 700 /run/time-archive

cat > /etc/tmpfiles.d/time-archive.conf <<'EOF'
d /run/time-archive 0700 root root -
EOF

log "Host directories and Docker service are ready"
