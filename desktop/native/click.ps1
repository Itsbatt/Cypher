param(
    [int]$X,
    [int]$Y,
    [string]$Title = 'Cypher'
)

Add-Type -Namespace W -Name U -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
[DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, System.UIntPtr extra);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(System.IntPtr hWnd);
'@

. (Join-Path $PSScriptRoot 'cypher-window.ps1')
$window = Get-CypherWindow -Title $Title
if (-not $window) { Write-Output 'no-window'; exit 1 }
[CW.Windows]::SetForegroundWindow($window.Handle) | Out-Null
Start-Sleep -Milliseconds 400

$absX = $window.Left + $X
$absY = $window.Top + $Y
Write-Output ("window={0},{1} {2}x{3}" -f $window.Left, $window.Top, $window.Width, $window.Height)
[W.U]::SetCursorPos($absX, $absY) | Out-Null
# The rail expands on hover; wait for the layout to settle before pressing.
Start-Sleep -Milliseconds 900
[W.U]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 60
[W.U]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
Write-Output ("clicked=" + $absX + "," + $absY)
