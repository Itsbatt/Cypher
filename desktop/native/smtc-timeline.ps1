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
$timeline = $session.GetTimelineProperties()
Write-Output ('timeline start=' + $timeline.StartTime + ' end=' + $timeline.EndTime + ' position=' + $timeline.Position)

$props = Await-WinRt $session.TryGetMediaPropertiesAsync() $propsType
Write-Output ('playing=' + $props.Title)
Write-Output ('thumb=' + [bool]$props.Thumbnail)

$seekTicks = [long](43.5 * 10000000)
Write-Output ('seek=' + (Await-WinRt $session.TryChangePlaybackPositionAsync($seekTicks) ([bool])))
Start-Sleep -Seconds 4
$props = Await-WinRt $session.TryGetMediaPropertiesAsync() $propsType
$info = $session.GetPlaybackInfo()
Write-Output ('after-end title=' + $props.Title + ' status=' + $info.PlaybackStatus)
