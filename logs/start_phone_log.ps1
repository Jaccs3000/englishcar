param(
    [string]$Serial = "ZY32HLST49"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$Adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $Adb)) {
    throw "adb.exe not found at $Adb"
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = Join-Path $PSScriptRoot "englishcar_phone_$Timestamp.log"
$LatestFile = Join-Path $PSScriptRoot "englishcar_phone_latest.log"

& $Adb -s $Serial logcat -c

$AppPidOutput = & $Adb -s $Serial shell pidof com.englishcar.voicecoach
$AppPid = if ($AppPidOutput) { $AppPidOutput.Trim() } else { "" }
$Args = @(
    "-s", $Serial, "logcat", "-v", "time",
    "EnglishCarDiagnostics:D",
    "EnglishCarConversation:D",
    "EnglishCarRealtime:D",
    "EnglishCarWebRTC:D",
    "EnglishCarBackendClient:D",
    "EnglishCarAudioTrack:D",
    "AndroidRuntime:E",
    "System.err:W",
    "*:S"
)
$Process = Start-Process -FilePath $Adb -ArgumentList $Args -RedirectStandardOutput $LogFile -RedirectStandardError (Join-Path $PSScriptRoot "englishcar_phone_logcat_error.log") -WindowStyle Hidden -PassThru

"pid=$($Process.Id)`nappPid=$AppPid`nserial=$Serial`nlog=$LogFile`nstarted=$(Get-Date -Format o)" | Set-Content (Join-Path $PSScriptRoot "englishcar_phone_logcat.pid")
"Log capture started.`nSerial: $Serial`nLogcat PID: $($Process.Id)`nApp PID: $AppPid`nLog: $LogFile"

if (Test-Path $LatestFile) {
    Remove-Item -LiteralPath $LatestFile -Force
}
New-Item -ItemType HardLink -Path $LatestFile -Target $LogFile | Out-Null
