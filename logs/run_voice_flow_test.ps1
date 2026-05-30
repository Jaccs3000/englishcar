$ErrorActionPreference = 'Continue'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = 'ZY32HLST49'
$log = 'C:\englishcar\logs\englishcar_phone_latest.log'
$result = 'C:\englishcar\logs\voice_flow_test_result.txt'
$play = 'C:\englishcar\test-audio\play_test_audio.ps1'
Set-Content -Path $result -Value "Voice flow test started $(Get-Date -Format o)" -Encoding UTF8
function Add-Result($m){ Add-Content -Path $result -Value $m -Encoding UTF8; Write-Host $m }
function ADB([string[]]$Args){ & $adb -s $serial @Args | Out-Null }
function Count-Pattern($pattern){ if(Test-Path $log){ return (Select-String -Path $log -Pattern $pattern).Count } return 0 }
function Wait-New($pattern,$baseline,$timeoutSec,$label){
  $deadline=(Get-Date).AddSeconds($timeoutSec)
  while((Get-Date) -lt $deadline){
    $count=Count-Pattern $pattern
    if($count -gt $baseline){ Add-Result "PASS $label count=$count baseline=$baseline"; return $true }
    Start-Sleep -Milliseconds 500
  }
  $count=Count-Pattern $pattern
  Add-Result "FAIL $label count=$count baseline=$baseline pattern=$pattern"
  return $false
}
function Play($name){ Add-Result "PLAY $name $(Get-Date -Format HH:mm:ss.fff)"; powershell -ExecutionPolicy Bypass -File $play -Name $name | Out-Null }

powershell -ExecutionPolicy Bypass -File C:\englishcar\logs\stop_phone_log.ps1 | Out-Null
Start-Sleep -Milliseconds 400
powershell -ExecutionPolicy Bypass -File C:\englishcar\logs\start_phone_log.ps1 -Serial $serial | Out-Null
Start-Sleep -Seconds 1

ADB @('shell','input','tap','540','1385')
$audioDone0=Count-Pattern 'Realtime: server audio done bytesReceived='
$resp0=Count-Pattern 'Realtime: response created startupGreeting=true'
$greetingCreated=Wait-New 'Realtime: response created startupGreeting=true' $resp0 35 'greeting response created'
$greetingDone=Wait-New 'Realtime: server audio done bytesReceived=' $audioDone0 45 'greeting audio done'
Start-Sleep -Seconds 2

$single0=Count-Pattern 'Realtime: accept transcript chars=.* words=1'
$respFalse0=Count-Pattern 'Realtime: response created startupGreeting=false'
Play 'single_project.wav'
$single=Wait-New 'Realtime: accept transcript chars=.* words=1' $single0 30 'single word transcript accepted'
$singleResp=Wait-New 'Realtime: response created startupGreeting=false' $respFalse0 45 'single word assistant response'
Start-Sleep -Seconds 4

$short0=Count-Pattern 'Realtime: accept transcript chars=.* words=[2-9]'
$respFalse1=Count-Pattern 'Realtime: response created startupGreeting=false'
Play 'short_work.wav'
$short=Wait-New 'Realtime: accept transcript chars=.* words=[2-9]' $short0 35 'short phrase transcript accepted'
$shortResp=Wait-New 'Realtime: response created startupGreeting=false' $respFalse1 45 'short phrase assistant response'
Start-Sleep -Seconds 4

$long0=Count-Pattern 'Realtime: accept transcript chars=([5-9][0-9]|[1-9][0-9][0-9])'
$respFalse2=Count-Pattern 'Realtime: response created startupGreeting=false'
Play 'long_project_update.wav'
$long=Wait-New 'Realtime: accept transcript chars=([5-9][0-9]|[1-9][0-9][0-9])' $long0 55 'long phrase transcript accepted'
$longResp=Wait-New 'Realtime: response created startupGreeting=false' $respFalse2 60 'long phrase assistant response'
Start-Sleep -Seconds 4

$very0=Count-Pattern 'Realtime: accept transcript chars=([1-9][0-9][0-9]|[5-9][0-9])'
$respFalse3=Count-Pattern 'Realtime: response created startupGreeting=false'
Play 'very_long_work_story.wav'
$very=Wait-New 'Realtime: accept transcript chars=([1-9][0-9][0-9]|[5-9][0-9])' $very0 80 'very long transcript accepted'
$veryResp=Wait-New 'Realtime: response created startupGreeting=false' $respFalse3 70 'very long assistant response'
Start-Sleep -Seconds 4

# Barge-in: trigger assistant with a short prompt, wait until it speaks, then play interrupt audio.
$respFalse4=Count-Pattern 'Realtime: response created startupGreeting=false'
$speaking0=Count-Pattern 'Conversation: realtimeEvent=Speaking'
$interrupt0=Count-Pattern 'Realtime: local confirmed barge-in interrupt|Realtime: interrupt assistant'
Play 'short_meeting.wav'
$preResp=Wait-New 'Realtime: response created startupGreeting=false' $respFalse4 45 'pre-interrupt assistant response'
$preSpeak=Wait-New 'Conversation: realtimeEvent=Speaking' $speaking0 45 'assistant speaking before interrupt'
Play 'interrupt_change_topic.wav'
$interrupt=Wait-New 'Realtime: local confirmed barge-in interrupt|Realtime: interrupt assistant' $interrupt0 30 'barge-in interrupt detected'
Start-Sleep -Seconds 5

ADB @('shell','input','tap','414','1426')
Start-Sleep -Milliseconds 600
ADB @('shell','input','tap','414','1426')

Add-Result "SUMMARY greetingCreated=$greetingCreated greetingDone=$greetingDone single=$single singleResp=$singleResp short=$short shortResp=$shortResp long=$long longResp=$longResp very=$very veryResp=$veryResp preResp=$preResp preSpeak=$preSpeak interrupt=$interrupt"
Add-Result "Voice flow test finished $(Get-Date -Format o)"
