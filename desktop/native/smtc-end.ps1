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
$manager = Await-WinRt ($managerType::RequestAsync()) $managerType
$session = $manager.GetSessions() | Where-Object { $_.SourceAppUserModelId -like '*cypher-smtc*' } | Select-Object -First 1
if (-not $session) { Write-Output 'no-session'; exit 2 }
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]

$before = Await-WinRt $session.TryGetMediaPropertiesAsync() $propsType
Write-Output ('before=' + $before.Title + ' status=' + $session.GetPlaybackInfo().PlaybackStatus)
Write-Output ('seek=' + (Await-WinRt $session.TryChangePlaybackPositionAsync([long](44.0 * 10000000)) ([bool])))
Start-Sleep -Seconds 4
$after = Await-WinRt $session.TryGetMediaPropertiesAsync() $propsType
Write-Output ('after=' + $after.Title + ' status=' + $session.GetPlaybackInfo().PlaybackStatus)
