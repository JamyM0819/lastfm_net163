$ErrorActionPreference = 'Stop'

$startup = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup'
$lnkPath = Join-Path $startup 'lastfm_net163.lnk'

$ws  = New-Object -ComObject WScript.Shell
$lnk = $ws.CreateShortcut($lnkPath)
$lnk.TargetPath       = 'C:\Windows\System32\wscript.exe'
$lnk.Arguments        = '"F:\reasonix_project\lastfm_net163\start_scrobbler_hidden.vbs"'
$lnk.WorkingDirectory = 'F:\reasonix_project\lastfm_net163'
$lnk.Description      = 'NetEase -> last.fm scrobbler (hidden autostart)'
$lnk.Save()

Write-Output "Created: $lnkPath"
