#!/bin/bash
set -e
BASE=/Users/aleksandrvorobev/Documents/github/Nuclear/Lazure
cd "$BASE"

# 1) Директории назначения
mkdir -p .github/workflows src/mobile/android gradle/wrapper

# 2) CI-воркфлоу -> корень
mv samsungrepo-main/.github/workflows/android-build.yml .github/workflows/android-build.yml

# 3) Android-приложение -> src/mobile/android
mv samsungrepo-main/app src/mobile/android

# 4) Корневые Gradle-конфиги и wrapper
mv samsungrepo-main/build.gradle.kts \
   samsungrepo-main/settings.gradle.kts \
   samsungrepo-main/gradle.properties \
   samsungrepo-main/gradlew \
   samsungrepo-main/gradlew.bat
mv samsungrepo-main/gradle/wrapper/* gradle/wrapper/
rmdir samsungrepo-main/gradle/wrapper
rmdir samsungrepo-main/gradle

# 5) Git-файлы -> корень
mv samsungrepo-main/.gitattributes samsungrepo-main/.gitignore

# 6) Сливаем README (до удаления папки-источника)
python3 - <<'PYEOF'
base = "/Users/aleksandrvorobev/Documents/github/Nuclear/Lazure"
s = open(base + "/samsungrepo-main/README.md", encoding="utf-8").read().splitlines(keepends=True)
body = "".join(s[1:])  # убираем заголовок '# Essential'
root = open(base + "/README.md", encoding="utf-8").read().rstrip("\n")
new = root + "\n\n---\n\n## Android-приложение (samsungrepo)\n\n" + body
open(base + "/README.md", "w", encoding="utf-8").write(new)
PYEOF

# 7) Удаляем пустую папку источника
rmdir samsungrepo-main

echo "INTEGRATION_DONE"
