param([string]$Image = 'mysql:8.0.42')

$ErrorActionPreference = 'Stop'
$containerName = 'memonogi-nickname-test-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$password = 'synthetic-nickname-test-only'
$created = $false

try {
  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is required for the MySQL migration test.'
  }

  $containerId = docker run --detach --name $containerName `
    --env "MYSQL_ROOT_PASSWORD=$password" --env 'MYSQL_DATABASE=auth' `
    --publish '127.0.0.1::3306' $Image
  if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated MySQL container.' }
  $created = $true

  $ready = $false
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
      docker exec --env "MYSQL_PWD=$password" $containerName mysqladmin ping --host=127.0.0.1 --user=root 2>$null | Out-Null
      $pingExitCode = $LASTEXITCODE
    } finally {
      $ErrorActionPreference = $previousPreference
    }
    if ($pingExitCode -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
  }
  if (-not $ready) { throw 'The isolated MySQL container did not become ready.' }

  $portText = docker port $containerName '3306/tcp'
  if ($LASTEXITCODE -ne 0 -or -not $portText) { throw 'Could not discover the MySQL port.' }
  $port = ($portText.Trim() -split ':')[-1]
  $env:NICKNAME_MYSQL_URL = "jdbc:mysql://127.0.0.1:$port/auth?useSSL=false&allowPublicKeyRetrieval=true"
  $env:NICKNAME_MYSQL_USER = 'root'
  $env:NICKNAME_MYSQL_PASSWORD = $password
  $env:NICKNAME_MYSQL_REQUIRED = 'true'

  & "$PSScriptRoot/../gradlew.bat" test --tests '*NicknameMigrationMySqlTest' --rerun-tasks --no-daemon
  if ($LASTEXITCODE -ne 0) { throw 'The MySQL migration test failed.' }
} finally {
  Remove-Item Env:NICKNAME_MYSQL_URL, Env:NICKNAME_MYSQL_USER, Env:NICKNAME_MYSQL_PASSWORD, Env:NICKNAME_MYSQL_REQUIRED -ErrorAction SilentlyContinue
  if ($created) { docker rm --force $containerName | Out-Null }
}
