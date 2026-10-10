$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
Write-Host 'Building and running tests...'
& mvn.cmd clean verify -B -ntp
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
& docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Docker deployment failed' }
Write-Host '商城 http://localhost:18080  Nacos http://localhost:18848/nacos  Grafana http://localhost:13000'
