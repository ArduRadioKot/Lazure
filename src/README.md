# Исходный код

| Каталог | Платформа | Что внутри |
|---|---|---|
| [`mobile/android`](mobile/android) | Android (Kotlin, Compose) | Приложение Lazure, Gradle-модуль `:app` |

## Инструкция по запуску и сборке проекта

Все команды выполняются из корня репозитория.

1. Установите JDK 21, Android SDK 36, NDK `27.1.12297006`, CMake `3.22.1`. В `local.properties` укажите `sdk.dir=...`.
2. Скачайте модели и JNI-библиотеку sherpa-onnx:

   ```bash
   bash tools/models/fetch_models.sh
   ```

3. Соберите APK для телефона:

   ```bash
   ./gradlew :app:assembleRelease -Plazure.abis=arm64-v8a
   ```

   Результат: `src/mobile/android/build/outputs/apk/release/app-release.apk`.

   Вместо этого можно собрать debug-версию для эмулятора и сразу её запустить:

   ```bash
   ./run-android.sh
   ```

4. Тесты:

   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

   ```bash
   ./gradlew :app:connectedDebugAndroidTest
   ```

Подробнее — в [README](../README.md) в корне.
