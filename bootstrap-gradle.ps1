$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$wrapperJar = Join-Path $projectRoot "gradle\wrapper\gradle-wrapper.jar"
$wrapperUrl = "https://raw.githubusercontent.com/gradle/gradle/v9.3.1/gradle/wrapper/gradle-wrapper.jar"

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $wrapperJar) | Out-Null
Invoke-WebRequest -Uri $wrapperUrl -OutFile $wrapperJar
Write-Host "Gradle Wrapper installed: $wrapperJar"
