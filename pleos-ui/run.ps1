$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $project
$jdk=Join-Path $env:USERPROFILE '.jdks\openjdk-17.0.2'
$java=Join-Path $jdk 'bin\java.exe'
function Device([string]$command){$result=& $script:java ('-Duser.home='+$env:USERPROFILE) -cp pleos-ui/build/tools DeviceAdb shell $command;if($LASTEXITCODE -ne 0){throw 'Local emulator command failed'};return ($result -join "`n")}
try{
    New-Item -ItemType Directory -Path 'pleos-ui\build\tools' -Force|Out-Null
    & (Join-Path $jdk 'bin\javac.exe') -d pleos-ui/build/tools pleos-ui/tools/DeviceAdb.java;if($LASTEXITCODE -ne 0){throw 'Device helper compile failed'}
    $deviceUp=$false
    try{$client=[Net.Sockets.TcpClient]::new('127.0.0.1',5555);$client.Dispose();$deviceUp=$true}catch{}
    if(!$deviceUp){
        & .\pleos-ui\tools\launch-user-desktop.ps1 -Program (Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe') -Arguments '-avd Pleos_Connect_2_1 -port 5554 -no-snapshot-load -no-boot-anim -scale 0.55'
        $deadline=(Get-Date).AddMinutes(4)
        do{Start-Sleep -Seconds 2;try{$client=[Net.Sockets.TcpClient]::new('127.0.0.1',5555);$client.Dispose();$deviceUp=$true}catch{};if((Get-Date)-gt $deadline){throw 'Pleos startup timed out'}}until($deviceUp)
    }
    $boot=Device 'while ! getprop sys.boot_completed | grep -q 1; do sleep 1; done; echo BOOT_READY'
    if($boot -notmatch 'BOOT_READY'){throw 'Pleos is not ready'}
    $packages=Device 'pm list packages --user 10 com.roundwise.pleos'
    if($packages -notmatch 'package:com.roundwise.pleos'){
        $apk=Join-Path $project 'pleos-ui\build\ROUNDWISE_Pleos.apk'
        if(!(Test-Path $apk)){throw 'Build the APK first: powershell -File pleos-ui\build.ps1'}
        & $java ('-Duser.home='+$env:USERPROFILE) -cp pleos-ui/build/tools DeviceAdb push $apk /data/local/tmp/roundwise-pleos.apk
        if($LASTEXITCODE -ne 0){throw 'APK transfer failed'}
        $installed=Device 'pm install -r /data/local/tmp/roundwise-pleos.apk'
        if($installed -notmatch 'Success'){throw $installed}
    }
    $started=Device 'am start --user 10 -W -n com.roundwise.pleos/.MainActivity'
    Write-Host $started
    if($started -notmatch 'Status: ok'){throw 'App launch failed'}
    $show=Join-Path $project 'pleos-ui\tools\show-roundwise.ps1'
    & .\pleos-ui\tools\launch-user-desktop.ps1 -Program "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Arguments ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+$show+'"')
}catch{Write-Host ('ROUNDWISE launch failed: '+$_.Exception.Message) -ForegroundColor Red;exit 1}
