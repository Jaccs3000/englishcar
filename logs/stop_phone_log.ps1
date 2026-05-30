$ErrorActionPreference = "SilentlyContinue"
$PidFile = Join-Path $PSScriptRoot "englishcar_phone_logcat.pid"
if (Test-Path $PidFile) {
    $Content = Get-Content $PidFile
    $PidLine = $Content | Where-Object { $_ -like "pid=*" } | Select-Object -First 1
    $ProcessId = $PidLine -replace "^pid=", ""
    if ($ProcessId) {
        Stop-Process -Id ([int]$ProcessId) -Force
    }
    Remove-Item -LiteralPath $PidFile -Force
}
"Log capture stopped."
