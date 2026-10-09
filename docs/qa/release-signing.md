# Release signing, R8 and version codes (for A0 to apply to `:app`)

Nothing here is applied by A6. A0 edits `app/build.gradle.kts` (the `// Signing config is wired by A6` comment marks the spot).
`.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`. Never commit any of them.

## 1. Create the key once (outside the repo)
```
keytool -genkeypair -v -keystore ~/drift-release.jks -alias drift -keyalg RSA -keysize 4096 -validity 10000
```
Back the `.jks` file and both passwords up somewhere offline; losing the key means users cannot update an installed build.

## 2. `keystore.properties` (repo root, gitignored)
```
storeFile=/home/krish/drift-release.jks
storePassword=<store password>
keyAlias=drift
keyPassword=<key password>
```
`storeFile` may be absolute or relative to the repo root. Add a committed `keystore.properties.example` with the same keys and empty values.
Without the file, the release build is produced unsigned (so CI and contributors can still run `assembleRelease`).

## 3. `app/build.gradle.kts` changes
At the top of the file:
```kotlin
import java.util.Properties

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
```
Inside `android { ... }`, before `buildTypes`:
```kotlin
    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            // minSdk is 28, so the legacy JAR scheme (v1) is not needed. v2 and v3 are required by the checklist.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }
```
and in the existing `release` build type add one line (keep the rest as is):
```kotlin
            signingConfig = if (keystoreProperties.isNotEmpty()) signingConfigs.getByName("release") else null
```

## 4. R8 full mode
AGP 8 runs R8 in full mode by default. The release build type already sets `isMinifyEnabled = true` and `isShrinkResources = true`.
Requirements:
- `gradle.properties` must not contain `android.enableR8.fullMode=false` (check: `grep -n fullMode gradle.properties` prints nothing, or says `true`).
  A0 may add `android.enableR8.fullMode=true` explicitly so the intent is visible.
- Keep rules go in `app/proguard-rules.pro` only for things libraries do not ship consumer rules for. After the first release build, run the app
  (or the benchmark module) on the release APK and fix any `ClassNotFoundException` / `NoSuchMethodException` with a narrow `-keep` rule, never `-dontobfuscate` or `-dontoptimize`.
- Evidence the build was shrunk: `app/build/outputs/mapping/release/mapping.txt` and `usage.txt` exist and are non-empty; the release APK is smaller than debug.

## 5. Build and verify
```
source scripts/env.sh
./gradlew :app:assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
BT=$ANDROID_HOME/build-tools/36.0.0

$BT/apksigner verify --verbose --print-certs "$APK"
```
Expected lines (v1 false is correct for minSdk 28):
```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
```
Also:
```
$BT/zipalign -c -P 16 -v 4 "$APK"            # 4-byte alignment and 16 KB page alignment for native libraries
$BT/aapt2 dump badging "$APK" | grep -E "package:|sdkVersion|targetSdkVersion"
$BT/aapt2 dump permissions "$APK"             # INTERNET must not appear
keytool -printcert -jarfile "$APK"            # certificate fingerprint; must equal the one recorded for the release key
```
Record the certificate SHA-256 in the checklist once; every later release must show the same value.

## 6. Version code bumping
- `versionCode` (integer, strictly increasing, never reused) and `versionName` (`major.minor.patch`) live in `app/build.gradle.kts` `defaultConfig`.
- Rule: bump `versionCode` by exactly 1 for every build that leaves the building (RC, beta, release), in the same commit that bumps `versionName`
  where the name changes. Betas and releases share one counter.
- Tag each shipped build: `git tag v<versionName>-<versionCode>`.
- Check before shipping, comparing against the last tag:
  ```
  LAST=$(git describe --tags --abbrev=0)
  git show "$LAST":app/build.gradle.kts | grep -E "versionCode|versionName"
  grep -E "versionCode|versionName" app/build.gradle.kts
  $BT/aapt2 dump badging "$APK" | grep -o "versionCode='[0-9]*'"
  ```
  The APK's versionCode must be greater than the last tag's, and the room schema version (if changed) must have a migration (checklist item 10).
