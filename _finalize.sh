#!/bin/bash
set -e
BASE=/Users/aleksandrvorobev/Documents/github/Nuclear/Lazure
cd "$BASE"

# 1) Размёрстываем src/mobile/android/app -> src/mobile/android (без вложенного app/)
cd src/mobile/android
shopt -s dotglob nullglob
mv app/* .
rmdir app
cd ../..

# 2) Переносим целый gradle/ (gradle-wrapper + libs.versions.toml + daemon props) в корень
cp -a samsungrepo-main/gradle/. gradle/
rmdir samsungrepo-main/gradle

# 3) Сливаем README ДО удаления папки-источника
python3 - <<'PYEOF'
base = "/Users/aleksandrvorobev/Documents/github/Nuclear/Lazure"
s = open(base + "/samsungrepo-main/README.md", encoding="utf-8").read().splitlines(keepends=True)
body = "".join(s[1:])  # убираем заголовок '# Essential'
root = open(base + "/README.md", encoding="utf-8").read().rstrip("\n")
open(base + "/README.md", "w", encoding="utf-8").write(root + "\n\n---\n\n## Android-приложение (samsungrepo)\n\n" + body)
PYEOF

# 4) Оставшиеся корневые файлы samsungrepo-main -> корень
mv samsungrepo-main/.gitattributes \
   samsungrepo-main/.gitignore \
   samsungrepo-main/build.gradle.kts \
   samsungrepo-main/settings.gradle.kts \
   samsungrepo-main/gradle.properties \
   samsungrepo-main/gradlew \
   samsungrepo-main/gradlew.bat \
   samsungrepo-main/README.md .

# 5) Удаляем пустую папку источника
rmdir samsungrepo-main

# 6) Чистка мусора (временный скрипт, IDE-артефакты)
rm -f _integrate.sh
rm -rf .idea .DS_Store

echo "INTEGRATION_DONE"
