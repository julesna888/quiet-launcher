#!/bin/sh
# Compila e firma l'APK. Servono: JDK 17+, ANDROID_HOME con platforms;android-28 e build-tools;34.0.0 (o BUILD_TOOLS=<versione>).
set -e
cd "$(dirname "$0")"
: "${JAVA_HOME:?imposta JAVA_HOME (JDK 17+)}"; : "${ANDROID_HOME:?imposta ANDROID_HOME}"
BT="$ANDROID_HOME/build-tools/${BUILD_TOOLS:-34.0.0}"; AJ="$ANDROID_HOME/platforms/android-28/android.jar"
KS="${KEYSTORE:-out/debug.keystore}"; KPASS="${KEYSTORE_PASS:-android}"
mkdir -p out/classes out/gen; rm -f out/*.apk out/*.zip out/*.dex
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets out/res.zip --java out/gen --min-sdk-version 21 --target-sdk-version 28 --version-code 1 --version-name 1.0
"$JAVA_HOME/bin/javac" --release 8 -Xlint:-options -cp "$AJ" -d out/classes out/gen/app/quiet/launcher/R.java src/app/quiet/launcher/*.java
"$BT/d8" --lib "$AJ" --release --min-api 21 --output out $(find out/classes -name '*.class')
(cd out && zip -q -j base.apk classes.dex)
"$BT/zipalign" -f 4 out/base.apk out/aligned.apk
[ -f "$KS" ] || "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass "$KPASS" -keypass "$KPASS" -alias quiet -keyalg RSA -keysize 2048 -validity 9000 -dname "CN=Quiet Launcher" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KPASS" --key-pass "pass:$KPASS" --out out/launcher.apk out/aligned.apk
echo "APK: app/out/launcher.apk"
