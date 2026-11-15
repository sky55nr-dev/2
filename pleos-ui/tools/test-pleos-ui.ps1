$ErrorActionPreference='Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$java=Join-Path $env:USERPROFILE '.jdks\openjdk-17.0.2\bin\java.exe'
New-Item -ItemType Directory -Path 'pleos-ui\build\tools' -Force|Out-Null
& (Join-Path $env:USERPROFILE '.jdks\openjdk-17.0.2\bin\javac.exe') -d pleos-ui/build/tools pleos-ui/tools/DeviceAdb.java
if($LASTEXITCODE -ne 0){throw 'Device helper compile failed'}
function Device([string]$command){$lines=& $java ('-Duser.home='+$env:USERPROFILE) -cp pleos-ui/build/tools DeviceAdb shell $command;if($LASTEXITCODE -ne 0){throw 'Device command failed'};return ($lines -join "`n")}
function Ui { $raw=Device 'uiautomator dump /sdcard/rw-test.xml; cat /sdcard/rw-test.xml';$start=$raw.IndexOf('<?xml');if($start -lt 0){throw 'No UI hierarchy'};$script:cachedDoc=[xml]$raw.Substring($start);return $script:cachedDoc }
function Tap([string]$text){$doc=$script:cachedDoc;if(!$doc){$doc=Ui};$node=$doc.SelectSingleNode("//node[@text='$text' or @content-desc='$text']");if(!$node){throw ('Missing control: '+$text)};$nums=[regex]::Matches($node.bounds,'\d+')|ForEach-Object {[int]$_.Value};$tapCommand='input -d 0 tap '+[int](($nums[0]+$nums[2])/2)+' '+[int](($nums[1]+$nums[3])/2);Write-Host ('TAP: '+$text+' '+$node.bounds+' -> '+$tapCommand);Device $tapCommand|Out-Null;$script:cachedDoc=$null}
function AssertText([string]$text){$doc=Ui;if(!(@($doc.SelectNodes('//node'))|Where-Object {$_.text -like ('*'+$text+'*')})){throw ('UI missing: '+$text)};Write-Host ('PASS: '+$text)}
Tap '여정';AssertText '집 도착 예상'
Tap '경로 비교';AssertText '나에게 맞는 여정 고르기'
Tap '이 여정 선택';AssertText '집 도착 예상'
Tap '설정';AssertText '내 이동 스타일';Tap '적용';AssertText '적용 중'
Tap '여정';Tap '여정 상세 · 일정 변경';AssertText '일정이 늦어졌나요?';Tap '15분 늦게';AssertText '귀가 경로 내비로 보내기'
Tap '여정 상세 · 일정 변경';Tap '출발 전 상태로 초기화';AssertText '차량 내비로 경로 보내기'
Tap '주변 통과로';AssertText '목적지 도착 예상';Tap '행사 방문으로';AssertText '집 도착 예상'
Write-Host 'ROUNDWISE_PLEOS_UI_CHECKS_PASSED'
