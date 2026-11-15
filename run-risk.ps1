$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$emulator=Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
try {
    $ErrorActionPreference='Continue'
    $devices=(& $adb devices 2>&1 | Out-String)
    $ErrorActionPreference='Stop'
    if($devices -notmatch '(?m)^emulator-5554\s+device') {
        Start-Process -FilePath $emulator -ArgumentList '-avd','Pleos_Connect_2_1','-port','5554','-no-snapshot-load','-no-boot-anim','-scale','0.55' -WindowStyle Normal
        $deadline=(Get-Date).AddMinutes(4)
        do {
            Start-Sleep -Seconds 2
            $ErrorActionPreference='Continue';$boot=(& $adb -s emulator-5554 shell getprop sys.boot_completed 2>&1 | Out-String);$ErrorActionPreference='Stop'
            if((Get-Date) -gt $deadline){throw 'Pleos boot timed out'}
        }until($boot.Trim() -eq '1')
    }
    $apk=Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
    & $adb -s emulator-5554 install -r $apk
    if($LASTEXITCODE -ne 0){throw 'App installation failed'}
    & $adb -s emulator-5554 shell am start -W -n com.riskmemory.map/.MainActivity
    if($LASTEXITCODE -ne 0){throw 'App launch failed'}
    & (Join-Path $PSScriptRoot 'show-risk.ps1')
} catch {Write-Host $_ -ForegroundColor Red;Read-Host 'Press Enter to close'}