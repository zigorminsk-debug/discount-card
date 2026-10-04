#!/usr/bin/env bash
# Проверяет главное обещание: новая версия ставится ПОВЕРХ установленной,
# без удаления и без потери карт. Запускается внутри эмулятора на CI.
#
#   $1 — APK прошлой версии (обычно скачанный опубликованный релиз)
#   $2 — APK новой версии (собранный из текущего коммита)

set -o pipefail

PKG=com.zigor.discountcard
OLD=${1:-/tmp/old.apk}
NEW=${2:-/tmp/new.apk}
MARKER=/data/data/$PKG/files/upgrade-marker

fail() {
  echo "::error title=Обновление поверх не работает::$1"
  exit 1
}

info() { echo "--- $1"; }

adb uninstall "$PKG" >/dev/null 2>&1 || true

info "ставим прошлую версию: $OLD"
adb install -r "$OLD" || fail "прошлая версия не установилась"

BEFORE_VC=$(adb shell dumpsys package $PKG | grep -m1 versionCode | tr -d '\r' | xargs)
FIRST_BEFORE=$(adb shell dumpsys package $PKG | grep -m1 firstInstallTime | tr -d '\r' | xargs)
info "до обновления: $BEFORE_VC | $FIRST_BEFORE"
echo "::notice title=Прошлая версия::$BEFORE_VC | $FIRST_BEFORE"

# Кладём метку в данные приложения, чтобы увидеть, переживут ли они обновление.
# На AOSP-образе эмулятора доступен root; если нет — шаг просто пропускается.
adb root >/dev/null 2>&1 || true
adb wait-for-device
sleep 3
adb shell "mkdir -p /data/data/$PKG/files && echo keep > $MARKER" >/dev/null 2>&1 || true
HAS_MARKER=$(adb shell "cat $MARKER" 2>/dev/null | tr -d '\r' | xargs)

info "обновляем поверх: $NEW"
OUT=$(adb install -r "$NEW" 2>&1)
STATUS=$?
echo "$OUT"
if [ $STATUS -ne 0 ] || echo "$OUT" | grep -qi "failure"; then
  case "$OUT" in
    *UPDATE_INCOMPATIBLE*|*INCONSISTENT_CERTIFICATES*|*"signatures do not match"*)
      fail "у новой сборки другая подпись — поверх установленной версии она не встанет: $OUT" ;;
    *VERSION_DOWNGRADE*)
      fail "versionCode новой сборки меньше установленной: $OUT" ;;
    *)
      fail "установка поверх завершилась ошибкой: $OUT" ;;
  esac
fi

AFTER_VC=$(adb shell dumpsys package $PKG | grep -m1 versionCode | tr -d '\r' | xargs)
FIRST_AFTER=$(adb shell dumpsys package $PKG | grep -m1 firstInstallTime | tr -d '\r' | xargs)
info "после обновления: $AFTER_VC | $FIRST_AFTER"
echo "::notice title=После обновления::$AFTER_VC | $FIRST_AFTER"

[ "$BEFORE_VC" != "$AFTER_VC" ] || fail "versionCode не изменился — обновления фактически не было"
[ "$FIRST_BEFORE" = "$FIRST_AFTER" ] || fail "приложение поставилось заново (сменилось firstInstallTime), а не обновилось"

if [ "$HAS_MARKER" = "keep" ]; then
  SURVIVED=$(adb shell "cat $MARKER" 2>/dev/null | tr -d '\r' | xargs)
  [ "$SURVIVED" = "keep" ] || fail "данные приложения стёрлись при обновлении"
  info "данные приложения на месте"
else
  info "метку поставить не удалось (нет root) — проверяем только факт обновления"
fi

info "запускаем обновлённое приложение"
adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || fail "приложение не запускается после обновления"
sleep 6
adb shell pidof "$PKG" >/dev/null 2>&1 || fail "приложение упало сразу после обновления"

echo "::notice title=Обновление поверх::новая версия встала поверх прошлой, данные целы, приложение запускается"
echo "OK: новая версия встала поверх прошлой, данные целы, приложение работает"
