# Rebuilds cypher-smtc.exe from SmtcHelper.cs using the C# compiler that ships with
# .NET Framework (no Windows SDK or dotnet SDK required) and copies it into resources.
#
#   powershell -ExecutionPolicy Bypass -File desktop/native/build-smtc.ps1
#
# The helper is intentionally tiny and dependency-free: it resolves WinRT types by name at
# runtime, so it needs no Windows Metadata reference at compile time.

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot

$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) {
    $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
}
if (-not (Test-Path $csc)) {
    throw 'No se encontró csc.exe (se requiere .NET Framework 4.x).'
}

& $csc /nologo /target:exe /out:cypher-smtc.exe /r:System.Core.dll SmtcHelper.cs
if ($LASTEXITCODE -ne 0) { throw "csc falló con código $LASTEXITCODE" }

$target = Join-Path $PSScriptRoot '..\src\main\resources\native\cypher-smtc.exe'
Copy-Item cypher-smtc.exe $target -Force
Write-Output ("SMTC helper -> " + (Resolve-Path $target))
Pop-Location
