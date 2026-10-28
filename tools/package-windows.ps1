# Developer packaging entry point. End users need only the generated EXE installer.
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/jpackage.exe'))) {
    throw 'Set JAVA_HOME to a JDK 25 installation before packaging.'
}
$wixDirectory = Join-Path $projectRoot 'prototype-compose/.gradle/packaging-tools/wix-3.14.1'
$archive = Join-Path $wixDirectory 'wix314-binaries.zip'
New-Item -ItemType Directory -Force -Path $wixDirectory | Out-Null
if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $archive
}
$expected = '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'WiX download checksum mismatch; investigate before retrying.'
}
Expand-Archive -LiteralPath $archive -DestinationPath $wixDirectory -Force
Push-Location $projectRoot
try {
    & cmd /d /c prototype-compose\gradlew.bat -p prototype-compose test packageExe "-PwixToolsetDir=$wixDirectory"
    if ($LASTEXITCODE -ne 0) { throw 'Packaging failed; inspect the Gradle diagnostics.' }
} finally { Pop-Location }
