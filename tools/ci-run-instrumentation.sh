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

# Снимки экранов, которые сделал ScreenshotTest. Папка приложения не годится:
# Gradle удаляет APK после тестов вместе с файлами, поэтому тест пишет снимки
# в общую папку от имени shell (screencap).
{
  echo "=== снимки экранов (screencap от shell, переживают удаление приложения) ==="
  adb shell ls -l /sdcard/Pictures/moi-karty-shots 2>&1 || true
  adb pull /sdcard/Pictures/moi-karty-shots/. "$SHOTS" 2>&1 || true
  adb shell rm -rf /sdcard/Pictures/moi-karty-shots 2>&1 || true

  echo "=== итог ==="
  ls -la "$SHOTS" 2>&1 || true
} 2>&1 | tee -a "$LOG"

exit $RC
