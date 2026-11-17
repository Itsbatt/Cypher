$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime

function Await-WinRt($operation, $resultType) {
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object {
            $_.Name -eq 'AsTask' -and
            $_.GetParameters().Count -eq 1 -and
            $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
        } | Select-Object -First 1
    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait(15000) | Out-Null
    return $task.Result
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$op = $managerType::RequestAsync()
$manager = Await-WinRt $op $managerType
$sessions = $manager.GetSessions()
Write-Output ('sessions=' + $sessions.Count)
foreach ($session in $sessions) {
    $info = $session.GetPlaybackInfo()
    $propsOp = $session.TryGetMediaPropertiesAsync()
    $props = Await-WinRt $propsOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime])
    Write-Output ('app=' + $session.SourceAppUserModelId +
        ' | status=' + $info.PlaybackStatus +
        ' | title=' + $props.Title +
        ' | artist=' + $props.Artist +
        ' | album=' + $props.AlbumTitle)
}
