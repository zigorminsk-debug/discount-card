#!/usr/bin/env bash
# Запуск инструментальных тестов внутри reactivecircus/android-emulator-runner:
# эмулятор уже загружен, adb доступен. Скрипт лежит отдельным файлом, чтобы не
# прятать логику в YAML (раннер выполняет script через sh, а не bash).

set -o pipefail

PKG=com.zigor.discountcard.debug
LOG=/tmp/test.log
SHOTS=/tmp/shots
mkdir -p "$SHOTS"

# Крупный системный шрифт — как на телефонах с увеличенным размером текста.
# Так ловятся переполнения вёрстки, которых не видно при стандартном масштабе.
adb shell settings put system font_scale 1.30
sleep 3

./gradlew connectedDebugAndroidTest --no-daemon 2>&1 | tee "$LOG"
RC=$?

adb shell settings put system font_scale 1.0 || true

# Снимки экранов, которые сделал ScreenshotTest. Путь во внешней памяти
# приложения на Android 11+ не читается обычным shell, поэтому есть запасной
# вариант через run-as (работает для debug-сборки).
{
  echo "=== снимки экранов: внешняя память приложения ==="
  adb shell ls -l "/sdcard/Android/data/$PKG/files/screenshots" 2>&1 || true
  adb pull "/sdcard/Android/data/$PKG/files/screenshots/." "$SHOTS" 2>&1 || true

  echo "=== снимки экранов: внутренняя память через run-as ==="
  for name in $(adb exec-out run-as "$PKG" ls files/screenshots 2>/dev/null | tr -d '\r'); do
    if [ ! -s "$SHOTS/$name" ]; then
      if adb exec-out run-as "$PKG" cat "files/screenshots/$name" > "$SHOTS/$name" 2>/dev/null; then
        echo "забрали через run-as: $name"
      else
        rm -f "$SHOTS/$name"
        echo "не удалось забрать: $name"
      fi
    fi
  done

  echo "=== итог ==="
  ls -la "$SHOTS" 2>&1 || true
} 2>&1 | tee -a "$LOG"

exit $RC
