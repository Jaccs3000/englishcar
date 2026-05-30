$ErrorActionPreference = "SilentlyContinue"
& (Join-Path $PSScriptRoot "stop_phone_log.ps1") | Out-Null
Get-ChildItem -LiteralPath $PSScriptRoot -Filter "englishcar_phone*.log" | Remove-Item -Force
Get-ChildItem -LiteralPath $PSScriptRoot -Filter "englishcar_phone*.pid" | Remove-Item -Force
"English Car phone logs cleared."
