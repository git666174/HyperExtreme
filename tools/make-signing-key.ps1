# Create fork signing key (ASCII only: PowerShell 5.1 mis-reads UTF-8 without BOM)
$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
New-Item -ItemType Directory -Force signing | Out-Null

$keytool = "D:\Deepseekwork\tools\jdk17\jdk-17.0.2\bin\keytool.exe"
if (-not (Test-Path $keytool)) { throw "keytool not found: $keytool" }

$ks = Join-Path (Get-Location) "signing\alive-fix.jks"
if (Test-Path $ks) {
    Write-Host "Existing file found, removing it first: $ks" -ForegroundColor Yellow
    Remove-Item -LiteralPath $ks -Force
}

$secure = Read-Host "Keystore password (input is hidden)" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
if ([string]::IsNullOrEmpty($plain)) { throw "empty password" }

$env:KS_PASS = $plain
try {
    & $keytool -genkeypair -keystore $ks -alias alivefix -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12 -storepass:env KS_PASS -keypass:env KS_PASS -dname "CN=HyperExtreme Alive, O=HyperExtreme Fork, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed, exit code $LASTEXITCODE" }

    $lines = @(
        "storeFile=signing/alive-fix.jks"
        ("storePassword=" + $plain)
        "keyAlias=alivefix"
        ("keyPassword=" + $plain)
    )
    Set-Content -Path (Join-Path (Get-Location) "keystore.properties") -Value $lines -Encoding ASCII

    Write-Host ""
    Write-Host "DONE" -ForegroundColor Green
    Write-Host ("  " + $ks)
    Write-Host "  keystore.properties (gitignored)"
} finally {
    Remove-Item Env:KS_PASS -ErrorAction SilentlyContinue
    $plain = $null
}
