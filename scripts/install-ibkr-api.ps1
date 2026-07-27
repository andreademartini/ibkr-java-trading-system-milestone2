param(
    [string]$JarPath = "C:\TWS API\source\JavaClient\TwsApi.jar",
    [string]$Version = "10.45.01"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "TwsApi.jar non trovato in '$JarPath'. Passa il percorso corretto con -JarPath."
}

$ResolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
Write-Host "Installazione di $ResolvedJar nel repository Maven locale..."

mvn org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file `
    "-Dfile=$ResolvedJar" `
    "-DgroupId=com.interactivebrokers" `
    "-DartifactId=tws-api" `
    "-Dversion=$Version" `
    "-Dpackaging=jar" `
    "-DgeneratePom=true"

if ($LASTEXITCODE -ne 0) {
    throw "Installazione Maven di TwsApi.jar non riuscita."
}

Write-Host "TwsApi.jar installato come com.interactivebrokers:tws-api:$Version"
