#!/usr/bin/env bash
# Builds VoiceRecorder.apk without Gradle, using only Android SDK build-tools.
#
# Requires: JDK 17, ANDROID_SDK_ROOT containing
#   build-tools/34.0.0  and  platforms/android-34
#
#   ./build.sh          -> build/voice-recorder.apk
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
MIN_SDK=24
TARGET_SDK=34

cd "$(dirname "$0")"
rm -rf build
mkdir -p build/gen build/classes build/dex

echo "[1/6] aapt2 compile"
"$BT/aapt2" compile --dir app/src/main/res -o build/res.zip

echo "[2/6] aapt2 link"
"$BT/aapt2" link -o build/base.apk \
  -I "$AJ" \
  --manifest app/src/main/AndroidManifest.xml \
  -R build/res.zip \
  --java build/gen \
  --min-sdk-version $MIN_SDK \
  --target-sdk-version $TARGET_SDK \
  --version-code 1 --version-name 1.0 \
  --auto-add-overlay

echo "[3/6] javac"
find app/src/main/java build/gen -name "*.java" > build/sources.txt
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -classpath "$AJ" -d build/classes @build/sources.txt

echo "[4/6] d8"
find build/classes -name "*.class" > build/classes.txt
"$BT/d8" --min-api $MIN_SDK --lib "$AJ" --output build/dex @build/classes.txt

echo "[5/6] package"
python3 - <<'PY'
import shutil, zipfile
shutil.copy('build/base.apk', 'build/withdex.apk')
with zipfile.ZipFile('build/withdex.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex', 'classes.dex')
PY
"$BT/zipalign" -f 4 build/withdex.apk build/aligned.apk

echo "[6/6] sign"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US"
fi
"$BT/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out build/voice-recorder.apk build/aligned.apk
"$BT/apksigner" verify build/voice-recorder.apk

echo "OK -> build/voice-recorder.apk"
