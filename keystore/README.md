# Подпись релизов постоянным ключом

Android разрешает установить обновление поверх приложения, **только если новая версия
подписана тем же ключом**. Поэтому ключ создаётся один раз и живёт вечно.

## Что уже сделано

* Создан ключ `keystore/release.p12` (RSA-4096, срок действия до 2061 года, алиас `discountcard`).
* Пароли лежат рядом в `keystore/keystore.properties`.
* Оба файла **не попадают в git** (см. `.gitignore`) — репозиторий публичный,
  а с чужим ключом подписи можно подсунуть пользователю поддельное обновление.

## Что нужно сделать один раз: положить ключ в GitHub Secrets

Вариант А — одной командой (нужен установленный `gh` и `gh auth login`):

```bash
./tools/setup-signing-secrets.sh
```

Вариант Б — вручную: **Settings → Secrets and variables → Actions → New repository secret**

| Имя секрета | Значение |
|---|---|
| `KEYSTORE_BASE64` | содержимое `keystore/release.p12.base64.txt` (одна длинная строка) |
| `KEYSTORE_PASSWORD` | `storePassword` из `keystore/keystore.properties` |
| `KEY_ALIAS` | `discountcard` |
| `KEY_PASSWORD` | `keyPassword` из `keystore/keystore.properties` (совпадает со storePassword) |

Пока секретов нет, сборка не падает: CI подпишет APK временным debug-ключом
и пометит файл суффиксом `-DEBUGKEY`. Такой APK ставится, но обновиться
поверх него «правильной» сборкой уже не получится — нужно будет удалить приложение.

## Резервная копия (важно!)

Скачайте и сохраните в надёжном месте (менеджер паролей, офлайн-носитель):

* `keystore/release.p12`
* `keystore/keystore.properties`

Потеря ключа = невозможность выпускать обновления для уже установленных копий.
GitHub Secrets прочитать обратно нельзя — это односторонняя дорога.

## Если ключ всё-таки потерян

```bash
rm -f keystore/release.p12 keystore/keystore.properties keystore.properties
./tools/make-keystore.sh          # создаст новый ключ (Java не нужна, хватает openssl)
./tools/setup-signing-secrets.sh  # зальёт его в секреты
```

Пользователям придётся удалить старую версию и поставить новую заново.

## Проверить, каким ключом подписан APK

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs -v BY-Card-v1.0.12.apk
```

Отпечаток SHA-256 печатается и в логе каждой сборки, и в описании каждого релиза —
у всех релизов он должен совпадать.
