param([switch]$Online)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$jdk=Join-Path $env:USERPROFILE '.jdks\openjdk-17.0.2'
$java=Join-Path $jdk 'bin\java.exe';$javac=Join-Path $jdk 'bin\javac.exe'
$env:JAVA_HOME=$jdk;$env:GRADLE_USER_HOME=Join-Path $env:USERPROFILE '.gradle'
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_HOME=$sdk
New-Item -ItemType Directory -Path '.gradle','tools\out','tests\out' -Force | Out-Null
if(-not(Test-Path -LiteralPath '.gradle\risk-debug.keystore')){Copy-Item -LiteralPath (Join-Path $env:USERPROFILE '.android\debug.keystore') -Destination '.gradle\risk-debug.keystore'}
$gradleOptions=@('--no-daemon','--no-watch-fs','--console=plain',('-Duser.home='+$env:USERPROFILE),'-PdemoDebugKeystore=.gradle/risk-debug.keystore')
if(-not $Online){$gradleOptions+='--offline'}
& $javac -encoding UTF-8 -d tests/out app/src/main/java/com/riskmemory/map/DriveEngine.java tests/DriveEngineTest.java
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& $java -cp tests/out DriveEngineTest
if($LASTEXITCODE -ne 0){throw 'Detector tests failed'}
& '.\gradlew.bat' processDebugResources @gradleOptions
if($LASTEXITCODE -ne 0){throw 'Resource processing failed'}
& $javac -encoding UTF-8 -d tools/out tools/CompileAndroid.java
if($LASTEXITCODE -ne 0){throw 'Compiler helper build failed'}
& $java -cp tools/out CompileAndroid (Join-Path $sdk 'platforms\android-35\android.jar') 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes' 'app/src/main/java/com/riskmemory/map/DriveEngine.java' 'app/src/main/java/com/riskmemory/map/MainActivity.java'
if($LASTEXITCODE -ne 0){throw 'Android source compilation failed'}
# Java was compiled above against actual SDK API classes; Gradle packages those verified outputs.
& '.\gradlew.bat' assembleDebug -x compileDebugJavaWithJavac @gradleOptions
if($LASTEXITCODE -ne 0){throw 'APK packaging failed'}
Write-Host 'APK ready: app\build\outputs\apk\debug\app-debug.apk' -ForegroundColor Green