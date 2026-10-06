#!/usr/bin/env bash
# Заливает постоянный ключ подписи в GitHub Secrets репозитория.
# Запускать ОДИН раз (и потом только если меняете ключ).
#
#   gh auth login            # если ещё не авторизованы
#   ./tools/setup-signing-secrets.sh [owner/repo]
#
set -euo pipefail

REPO="${1:-$(git config --get remote.origin.url | sed -E 's#.*github\.com[:/]([^/]+/[^/.]+)(\.git)?#\1#')}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="$ROOT/keystore/release.p12"
PROPS="$ROOT/keystore/keystore.properties"

[ -f "$KEYSTORE" ] || { echo "Нет файла $KEYSTORE — создайте ключ: ./tools/make-keystore.sh"; exit 1; }
[ -f "$PROPS" ]    || { echo "Нет файла $PROPS";    exit 1; }

get() { grep -E "^$1=" "$PROPS" | cut -d'=' -f2- ; }

STORE_PASS="$(get storePassword)"
KEY_ALIAS="$(get keyAlias)"
KEY_PASS="$(get keyPassword)"

echo "Репозиторий: $REPO"
base64 -w0 "$KEYSTORE" | gh secret set KEYSTORE_BASE64   --repo "$REPO"
printf '%s' "$STORE_PASS"  | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_ALIAS"   | gh secret set KEY_ALIAS         --repo "$REPO"
printf '%s' "$KEY_PASS"    | gh secret set KEY_PASSWORD      --repo "$REPO"

echo
echo "Готово. Секреты в репозитории:"
gh secret list --repo "$REPO"
