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
Write-Output ('found=' + [bool]$session)
if (-not $session) { exit 2 }

$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]

function Show-State($label) {
    $info = $session.GetPlaybackInfo()
    $props = Await-WinRt $session.TryGetMediaPropertiesAsync() $propsType
    Write-Output ($label + ' | status=' + $info.PlaybackStatus + ' | title=' + $props.Title + ' | artist=' + $props.Artist)
}

Show-State 'before'
Write-Output ('toggle=' + (Await-WinRt $session.TryTogglePlayPauseAsync() ([bool])))
Start-Sleep -Milliseconds 900
Show-State 'after-pause'
Write-Output ('toggle=' + (Await-WinRt $session.TryTogglePlayPauseAsync() ([bool])))
Start-Sleep -Milliseconds 900
Show-State 'after-resume'
Write-Output ('next=' + (Await-WinRt $session.TrySkipNextAsync() ([bool])))
Start-Sleep -Milliseconds 1200
Show-State 'after-next'
Write-Output ('previous=' + (Await-WinRt $session.TrySkipPreviousAsync() ([bool])))
Start-Sleep -Milliseconds 1200
Show-State 'after-previous'
