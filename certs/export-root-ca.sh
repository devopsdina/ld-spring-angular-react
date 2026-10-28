#!/usr/bin/env bash
# Exports a root CA from the macOS keychains into this folder as a PEM .crt file, for the server
# container to trust (see certs/README.md).
#
#   ./certs/export-root-ca.sh "Cato"            # writes certs/cato.crt
#   ./certs/export-root-ca.sh "Cato" corp-root  # writes certs/corp-root.crt
set -euo pipefail

name="${1:?Usage: export-root-ca.sh <certificate name to match> [output name]}"
out_name="${2:-$(echo "$name" | tr '[:upper:] ' '[:lower:]-')}"
out="$(cd "$(dirname "$0")" && pwd)/${out_name}.crt"

# Roots installed by IT or MDM live in the System keychain; public roots in SystemRootCertificates.
pem="$(security find-certificate -a -c "$name" -p \
  /Library/Keychains/System.keychain \
  /System/Library/Keychains/SystemRootCertificates.keychain \
  "$HOME/Library/Keychains/login.keychain-db" 2>/dev/null || true)"

if [ -z "$pem" ]; then
  echo "No certificate matching \"$name\" found in the System, SystemRootCertificates or login keychains." >&2
  exit 1
fi

printf '%s\n' "$pem" > "$out"
echo "Wrote $out:"
openssl crl2pkcs7 -nocrl -certfile "$out" | openssl pkcs7 -print_certs -noout | grep -E 'subject|issuer'
