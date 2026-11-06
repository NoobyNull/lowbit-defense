#!/bin/bash
# Builds the APK without Gradle: javac -> d8 -> aapt2 -> zipalign -> apksigner.
# Runs the headless rule tests first. Output: build/LowbitDefense.apk
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
BT="$SDK/build-tools/37.0.0"
JAR="$SDK/platforms/android-37.0/android.jar"
PKG=com/matthew/lowbitdefense
OUT=build
KS="${KS_PATH:-$HOME/.android/lowbit-defense.jks}"   # signing key lives outside the repo
KS_PASS="${KS_PASS:-android}"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/test"

# 1. Rule tests on a plain JVM (no Android).
javac -d "$OUT/test" src/$PKG/Game.java test/$PKG/GameTest.java
java -ea -cp "$OUT/test" com.matthew.lowbitdefense.GameTest

# 2. Compile the app and convert to dex.
javac --release 11 -Xlint:-options -classpath "$JAR" -d "$OUT/classes" src/$PKG/*.java
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')

# 3. Link manifest, add dex, align.
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/app-unsigned.apk" -I "$JAR" --manifest AndroidManifest.xml "$OUT/res.zip"
(cd "$OUT" && jar --update --file app-unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

# 4. Sign (creates the key once; back it up, updates must use the same key).
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias lowbit -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Lowbit Defense"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --ks-key-alias lowbit --out "$OUT/LowbitDefense.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" verify "$OUT/LowbitDefense.apk"
echo "built $OUT/LowbitDefense.apk"
