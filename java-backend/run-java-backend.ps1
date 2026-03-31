$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$javaDir = Join-Path $root "java-backend"
$srcDir = Join-Path $javaDir "src"
$buildDir = Join-Path $javaDir "build"
$sqliteJar = Join-Path $javaDir "lib\\sqlite-jdbc.jar"

if (-not (Test-Path $sqliteJar)) {
    throw "Missing dependency: $sqliteJar"
}

New-Item -ItemType Directory -Force $buildDir | Out-Null

$classpath = @(
    (Join-Path $javaDir "lib\\sqlite-jdbc.jar")
    (Join-Path $javaDir "lib\\slf4j-api.jar")
    (Join-Path $javaDir "lib\\slf4j-simple.jar")
) -join ";"

javac -cp $classpath -d $buildDir (Join-Path $srcDir "CivicWatchServer.java")
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed."
}

java -cp "$buildDir;$classpath" CivicWatchServer
