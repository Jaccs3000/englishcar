param(
  [Parameter(Mandatory=$true)]
  [string]$Name
)
$path = Join-Path $PSScriptRoot $Name
if (!(Test-Path $path)) {
  Write-Error "Audio file not found: $path"
  exit 1
}
$player = New-Object System.Media.SoundPlayer $path
$player.Load()
$player.PlaySync()
