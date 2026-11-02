param(
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'),
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectDir
$env:GRADLE_USER_HOME = Join-Path $projectDir '.gradle-home'
if (Test-Path -LiteralPath (Join-Path $env:USERPROFILE '.gradle\caches\modules-2')) {
    $env:GRADLE_RO_DEP_CACHE = Join-Path $env:USERPROFILE '.gradle\caches'
}
$javaCandidates = @(
    $env:JAVA_HOME,
    'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\jbr',
    'C:\Program Files\Android\Android Studio\jbr'
)
$javaDir = $javaCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe')) } | Select-Object -First 1
if (!$javaDir) { throw 'Set JAVA_HOME to a JDK 17 or newer installation.' }
$env:JAVA_HOME = $javaDir
if (!(Test-Path -LiteralPath (Join-Path $projectDir '.tools\debug.keystore'))) {
    New-Item -ItemType Directory -Path (Join-Path $projectDir '.tools') -Force | Out-Null
    & (Join-Path $javaDir 'bin\keytool.exe') -genkeypair -keystore (Join-Path $projectDir '.tools\debug.keystore') -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw 'Could not create local debug signing key.' }
}
$gradleDist = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.2.1-bin'
$gradleExe = if (Test-Path -LiteralPath $gradleDist) { Get-ChildItem -LiteralPath $gradleDist -Recurse -File -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName }
if (!$gradleExe) { $gradleExe = Join-Path $projectDir 'gradlew.bat' }
$arguments = @('--no-daemon', '--console=plain')
if ($Offline) { $arguments += '--offline' }
$arguments += $Tasks
& $gradleExe @arguments
exit $LASTEXITCODE

