param(
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Title = 'Cypher'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

. (Join-Path $PSScriptRoot 'cypher-window.ps1')
$window = Get-CypherWindow -Title $Title
if (-not $window) { Write-Output 'no-window'; exit 1 }
[CW.Windows]::SetForegroundWindow($window.Handle) | Out-Null
Start-Sleep -Milliseconds 400
$bmp = New-Object System.Drawing.Bitmap $window.Width, $window.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($window.Left, $window.Top, 0, 0, $bmp.Size)
$dir = Join-Path $PSScriptRoot '..\screenshots'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$out = Join-Path $dir ($Name + '.png')
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
Write-Output $out
