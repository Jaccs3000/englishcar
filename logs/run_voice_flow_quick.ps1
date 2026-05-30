$ErrorActionPreference='Continue'
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial='ZY32HLST49'
$log='C:\englishcar\logs\englishcar_phone_latest.log'
$result='C:\englishcar\logs\voice_flow_quick_result.txt'
$play='C:\englishcar\test-audio\play_test_audio.ps1'
Set-Content $result "Quick voice flow test $(Get-Date -Format o)" -Encoding UTF8
function Say($m){ Write-Host $m; Add-Content $result $m -Encoding UTF8 }
function C($p){ if(Test-Path $log){ (Select-String -Path $log -Pattern $p).Count } else { 0 } }
function WaitNew($p,$base,$sec,$label){
  $end=(Get-Date).AddSeconds($sec)
  while((Get-Date)-lt $end){ $n=C $p; if($n -gt $base){ Say "PASS $label ($base->$n)"; return $true }; Start-Sleep -Milliseconds 400 }
  $n=C $p; Say "FAIL $label ($base->$n) pattern=$p"; return $false
}
function Play($name){ Say "PLAY $name"; powershell -ExecutionPolicy Bypass -File $play -Name $name | Out-Null }

# Ensure log capture is alive; do not block if stop/start has issues.
powershell -ExecutionPolicy Bypass -File C:\englishcar\logs\stop_phone_log.ps1 | Out-Null
Start-Sleep -Milliseconds 300
powershell -ExecutionPolicy Bypass -File C:\englishcar\logs\start_phone_log.ps1 -Serial $serial | Out-Null
Start-Sleep -Seconds 1

# Tap PLAY
& $adb -s $serial shell input tap 540 1385 | Out-Null
$g0=C 'Realtime: response created startupGreeting=true'
$a0=C 'Realtime: server audio done bytesReceived='
WaitNew 'Realtime: response created startupGreeting=true' $g0 25 'saludo creado' | Out-Null
WaitNew 'Realtime: server audio done bytesReceived=' $a0 35 'saludo audio terminado' | Out-Null
Start-Sleep -Seconds 1

$s0=C 'Realtime: accept transcript chars=.* words=1'
$r0=C 'Realtime: response created startupGreeting=false'
Play 'single_project.wav'
WaitNew 'Realtime: accept transcript chars=.* words=1' $s0 20 'palabra unica aceptada' | Out-Null
WaitNew 'Realtime: response created startupGreeting=false' $r0 25 'respuesta a palabra unica' | Out-Null
Start-Sleep -Seconds 2

$sh0=C 'Realtime: accept transcript chars=.* words=[2-9]'
$r1=C 'Realtime: response created startupGreeting=false'
Play 'short_work.wav'
WaitNew 'Realtime: accept transcript chars=.* words=[2-9]' $sh0 22 'frase corta aceptada' | Out-Null
WaitNew 'Realtime: response created startupGreeting=false' $r1 28 'respuesta a frase corta' | Out-Null
Start-Sleep -Seconds 2

$l0=C 'Realtime: accept transcript chars=([5-9][0-9]|[1-9][0-9][0-9])'
$r2=C 'Realtime: response created startupGreeting=false'
Play 'long_project_update.wav'
WaitNew 'Realtime: accept transcript chars=([5-9][0-9]|[1-9][0-9][0-9])' $l0 35 'frase larga aceptada' | Out-Null
WaitNew 'Realtime: response created startupGreeting=false' $r2 35 'respuesta a frase larga' | Out-Null
Start-Sleep -Seconds 2

# Interruption: prompt, wait speaking, then interrupt.
$r3=C 'Realtime: response created startupGreeting=false'
$sp0=C 'Conversation: realtimeEvent=Speaking'
$i0=C 'Realtime: local confirmed barge-in interrupt|Realtime: interrupt assistant'
Play 'short_meeting.wav'
WaitNew 'Realtime: response created startupGreeting=false' $r3 25 'respuesta previa interrupcion' | Out-Null
WaitNew 'Conversation: realtimeEvent=Speaking' $sp0 20 'asistente hablando' | Out-Null
Play 'interrupt_change_topic.wav'
WaitNew 'Realtime: local confirmed barge-in interrupt|Realtime: interrupt assistant' $i0 20 'interrupcion detectada' | Out-Null

Say '--- LOG TAIL ---'
Select-String -Path $log -Pattern 'response created|server audio done|accept transcript|ignore transcript|send response.create|local confirmed|interrupt assistant|Failed|failed|exception|websocket connected' | Select-Object -Last 80 | ForEach-Object { Add-Content $result $_.Line -Encoding UTF8 }
Say "DONE $(Get-Date -Format o)"
Get-Content $result
