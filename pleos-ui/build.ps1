$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$jdk=Join-Path $env:USERPROFILE '.jdks\openjdk-17.0.2'
$env:JAVA_HOME=$jdk
$java=Join-Path $jdk 'bin\java.exe';$javac=Join-Path $jdk 'bin\javac.exe'
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools=Join-Path $sdk 'build-tools\34.0.0'
$android=Join-Path $sdk 'platforms\android-35\android.jar'
New-Item -ItemType Directory -Path 'pleos-ui\build\classes','pleos-ui\build\tools','pleos-ui\build\dex' -Force|Out-Null
if(!(Test-Path 'pleos-ui\build\debug.keystore')){
    & (Join-Path $jdk 'bin\keytool.exe') -genkeypair -keystore pleos-ui/build/debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=ROUNDWISE Local Development'
    if($LASTEXITCODE -ne 0){throw 'Local signing key creation failed'}
}
& $javac -encoding UTF-8 -d pleos-ui/build/tools pleos-ui/tools/CompileAndroid.java
if($LASTEXITCODE -ne 0){throw 'Compiler helper failed'}
$sources=@(Get-ChildItem -LiteralPath 'pleos-ui\src' -Recurse -Filter '*.java'|ForEach-Object {$_.FullName})
& $java -cp pleos-ui/build/tools CompileAndroid $android 'pleos-ui/build/classes' @sources
if($LASTEXITCODE -ne 0){throw 'Native UI compilation failed'}
$classes=@(Get-ChildItem -LiteralPath 'pleos-ui\build\classes' -Recurse -Filter '*.class'|ForEach-Object {$_.FullName})
& $java -cp (Join-Path $tools 'lib\d8.jar') com.android.tools.r8.D8 --lib $android --min-api 28 --output pleos-ui/build/dex @classes
if($LASTEXITCODE -ne 0){throw 'UI dex compilation failed'}
& (Join-Path $tools 'aapt2.exe') link -I $android --manifest pleos-ui/AndroidManifest.xml -o pleos-ui/build/manifest.apk
if($LASTEXITCODE -ne 0){throw 'Manifest compile failed'}
& $javac -encoding UTF-8 -d pleos-ui/build/tools pleos-ui/tools/PackageApk.java
if($LASTEXITCODE -ne 0){throw 'Packager compile failed'}
$unsigned=Join-Path $root 'pleos-ui\build\roundwise-pleos-unsigned.apk'
& $java -cp pleos-ui/build/tools PackageApk 'apk/ROUNDWISE_release.apk' 'pleos-ui/build/manifest.apk' 'pleos-ui/build/dex' $unsigned
if($LASTEXITCODE -ne 0){throw 'APK packaging failed'}
$aligned=Join-Path $root 'pleos-ui\build\roundwise-pleos-aligned.apk'
& (Join-Path $tools 'zipalign.exe') -f -p 4 $unsigned $aligned
if($LASTEXITCODE -ne 0){throw 'APK alignment failed'}
$final=Join-Path $root 'pleos-ui\build\ROUNDWISE_Pleos.apk'
& (Join-Path $tools 'apksigner.bat') sign --ks 'pleos-ui\build\debug.keystore' --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $final $aligned
if($LASTEXITCODE -ne 0){throw 'APK signing failed'}
& (Join-Path $tools 'apksigner.bat') verify $final
if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
Write-Host ('PLEOS_UI_APK_READY: '+$final)
