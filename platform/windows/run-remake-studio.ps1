param([Parameter(Mandatory = $true)][string]$Root)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $Root).ProviderPath
$subst = Join-Path $env:SystemRoot 'System32\subst.exe'
$drive = $null
$pushed = $false
$exitCode = 1
$previousJava = $env:JAVA_HOME

try {
    $pending = Join-Path $rootPath '.remarkstudio-pending\generator-fabric-26.1.2-2026.2.zip'
    if (Test-Path -LiteralPath $pending) {
        $target = Join-Path $rootPath 'plugins\generator-fabric-26.1.2-2026.2.zip'
        $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss', [Globalization.CultureInfo]::InvariantCulture)
        $backup = Join-Path $rootPath ".remarkstudio-backups\plugin-update-$stamp.zip"
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup)) | Out-Null
        try {
            if (Test-Path -LiteralPath $target) {
                [IO.File]::Replace($pending, $target, $backup)
            } else {
                [IO.File]::Move($pending, $target)
            }
        } catch {
            throw 'Save your work and close the other Studio window before starting Remake Studio. The pending update is kept.'
        }
    }

    $occupied = [Environment]::GetLogicalDrives()
    foreach ($letter in @('R','S','T','U','V','W','X','Y','Z','Q','P','O','N','M','L','K','J','I','H','G','F','E','D')) {
        $candidate = $letter + ':'
        if ($occupied -contains ($candidate + '\')) { continue }
        & $subst $candidate $rootPath 2>$null
        if ($LASTEXITCODE -eq 0) {
            $drive = $candidate
            break
        }
    }
    if ($null -eq $drive) { throw 'No free temporary drive is available. Existing drive mappings were not changed.' }

    $java = Join-Path ($drive + '\') 'jdk\jbr25_win_64'
    if (!(Test-Path -LiteralPath (Join-Path $java 'bin\java.exe'))) {
        throw 'The bundled Windows Java runtime is missing from this installation.'
    }
    $env:JAVA_HOME = $java
    Push-Location -LiteralPath ($drive + '\')
    $pushed = $true
    & '.\gradlew.bat' 'runRemakeStudio'
    $exitCode = $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
} finally {
    if ($pushed) { Pop-Location }
    $env:JAVA_HOME = $previousJava
    # Release only the drive allocated by this invocation.
    if ($null -ne $drive) { & $subst $drive /d 2>$null }
}
exit $exitCode
