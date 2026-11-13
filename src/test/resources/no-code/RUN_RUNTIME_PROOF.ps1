param(
    [Parameter(Mandatory = $true)][string]$ExampleRoot,
    [Parameter(Mandatory = $true)][string]$JavaRuntime,
    [ValidatePattern('^21\.1\.[0-9]+$')][string]$NeoForgeVersion = '21.1.219'
)
$ErrorActionPreference = 'Stop'
$example = (Resolve-Path -LiteralPath $ExampleRoot).Path
$javaExe = Join-Path $JavaRuntime 'bin/java.exe'
$argsRoot = Join-Path $example 'build/moddev'
$folders = @('build/classes/java/main', 'build/resources/main',
    'build/classes/java/runtimeProof', 'build/resources/runtimeProof')
$modFolders = ($folders | ForEach-Object { 'mcreator%%' + (Join-Path $example $_).Replace('\', '/') }) -join ';'
$classpath = ((Get-Content -LiteralPath (Join-Path $argsRoot 'noCodeGameTestLegacyClasspath.txt')) +
	(Join-Path $argsRoot "artifacts/neoforge-$NeoForgeVersion.jar") +
	(Join-Path $argsRoot "artifacts/neoforge-$NeoForgeVersion-client-extra-aka-minecraft-resources.jar") +
    ($folders | ForEach-Object { Join-Path $example $_ })) -join ';'
$structureFile = Join-Path $example 'build/resources/runtimeProof/data/remark_first_mod/structure/empty.nbt'
$structureArgs = Join-Path $argsRoot 'no-code-structure.args'
[IO.File]::WriteAllLines($structureArgs, @('-cp', ('"' + $classpath.Replace('\', '/') + '"'),
    'proof.WriteEmptyStructure', ('"' + $structureFile.Replace('\', '/') + '"')), [Text.UTF8Encoding]::new($false))
& $javaExe ('@' + $structureArgs)
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the GameTest structure' }
$arguments = @('-Xmx2G', ('"-Dfml.modFolders=' + $modFolders + '"'), '-cp',
    ('"' + $classpath.Replace('\', '/') + '"'))
$arguments += Get-Content -LiteralPath (Join-Path $argsRoot 'noCodeGameTestRunVmArgs.txt')
$arguments += Get-Content -LiteralPath (Join-Path $argsRoot 'noCodeGameTestRunProgramArgs.txt')
$argumentFile = Join-Path $argsRoot 'no-code-runtime-proof.args'
[IO.File]::WriteAllLines($argumentFile, $arguments, [Text.UTF8Encoding]::new($false))
Push-Location -LiteralPath (Join-Path $example 'run-proof')
try {
    & $javaExe ('@' + $argumentFile)
    $result = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $result
