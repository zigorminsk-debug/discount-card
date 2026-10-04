#!/usr/bin/env bash
# Создаёт постоянный ключ подписи (PKCS12) без установленной Java — только openssl.
# Ключ действует ~35 лет. ОДИН ключ на всю жизнь приложения:
# потеряете — обновления поверх установленной версии станут невозможны.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/keystore/release.p12"
PROPS="$ROOT/keystore/keystore.properties"
ALIAS="${KEY_ALIAS:-discountcard}"

[ -f "$OUT" ] && { echo "Ключ уже существует: $OUT (удалите вручную, если точно нужен новый)"; exit 1; }
mkdir -p "$ROOT/keystore"

PASS="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 40)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

openssl req -x509 -newkey rsa:4096 -sha256 -days 12775 -nodes \
  -keyout "$TMP/key.pem" -out "$TMP/cert.pem" \
  -subj "/CN=Discount Card Release/OU=Android/O=${GITHUB_OWNER:-owner}/C=BY"

openssl pkcs12 -export -inkey "$TMP/key.pem" -in "$TMP/cert.pem" \
  -name "$ALIAS" -out "$OUT" -passout pass:"$PASS" \
  -macalg sha256 -keypbe aes-256-cbc -certpbe aes-256-cbc

cat > "$PROPS" <<PROPS_EOF
# ЛОКАЛЬНЫЙ файл, в git не попадает.
storeFile=keystore/release.p12
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
PROPS_EOF

cp "$PROPS" "$ROOT/keystore.properties"
chmod 600 "$OUT" "$PROPS" "$ROOT/keystore.properties"

echo "Создан $OUT"
openssl pkcs12 -in "$OUT" -passin pass:"$PASS" -nokeys -clcerts 2>/dev/null |
  openssl x509 -noout -subject -enddate -fingerprint -sha256
echo "Дальше: ./tools/setup-signing-secrets.sh"
