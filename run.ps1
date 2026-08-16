# ============================================================
# run.ps1 — One-click launcher for Calendly-Lite
# ============================================================
# Usage: Right-click this file -> "Run with PowerShell"
#        OR in a terminal: .\run.ps1
# ============================================================

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "   Calendly-Lite — Starting Up              " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# ── Step 1: Check PostgreSQL ─────────────────────────────────
Write-Host "[1/4] Checking PostgreSQL..." -ForegroundColor Yellow
$pg = Get-Service "postgresql-x64-17" -ErrorAction SilentlyContinue
if ($null -eq $pg) {
    Write-Host "      ERROR: PostgreSQL service not found." -ForegroundColor Red
    Write-Host "      Install PostgreSQL 17 from https://www.enterprisedb.com/downloads/postgres-postgresql-downloads" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
if ($pg.Status -ne "Running") {
    Write-Host "      PostgreSQL is stopped. Starting it..." -ForegroundColor Yellow
    Start-Service "postgresql-x64-17"
    Start-Sleep -Seconds 2
}
Write-Host "      PostgreSQL is RUNNING ✓" -ForegroundColor Green

# ── Step 2: Check Redis ──────────────────────────────────────
Write-Host "[2/4] Checking Redis..." -ForegroundColor Yellow
$redis = Get-Service "Redis" -ErrorAction SilentlyContinue
if ($null -eq $redis) {
    Write-Host "      ERROR: Redis service not found." -ForegroundColor Red
    Write-Host "      Install Redis with: winget install Redis.Redis" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
if ($redis.Status -ne "Running") {
    Write-Host "      Redis is stopped. Starting it..." -ForegroundColor Yellow
    Start-Service "Redis"
    Start-Sleep -Seconds 2
}
Write-Host "      Redis is RUNNING ✓" -ForegroundColor Green

# ── Step 3: Set Java 17 ──────────────────────────────────────
Write-Host "[3/4] Setting Java 17..." -ForegroundColor Yellow
$javaPath = "C:\Program Files\Java\jdk-17"
if (-not (Test-Path $javaPath)) {
    Write-Host "      ERROR: Java 17 not found at $javaPath" -ForegroundColor Red
    Write-Host "      Install Java 17 from https://www.oracle.com/java/technologies/downloads/#java17" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
$env:JAVA_HOME = $javaPath
$env:PATH = "$javaPath\bin;$env:PATH"
Write-Host "      Java 17 is SET ✓" -ForegroundColor Green

# ── Step 4: Find Maven ───────────────────────────────────────
Write-Host "[4/4] Locating Maven..." -ForegroundColor Yellow
$mvnCmd = "C:\Users\vicky\AppData\Local\Temp\maven\apache-maven-3.9.6\bin\mvn.cmd"
if (-not (Test-Path $mvnCmd)) {
    # Fallback: try PATH
    $mvnInPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnInPath) {
        $mvnCmd = $mvnInPath.Source
    } else {
        Write-Host "      Maven not found. Downloading Maven 3.9.6..." -ForegroundColor Yellow
        $tempDir = "$env:TEMP\maven"
        New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
        Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip" `
            -OutFile "$env:TEMP\maven.zip" -UseBasicParsing
        Expand-Archive "$env:TEMP\maven.zip" -DestinationPath $tempDir -Force
        $mvnCmd = "$tempDir\apache-maven-3.9.6\bin\mvn.cmd"
    }
}
Write-Host "      Maven found ✓" -ForegroundColor Green

# ── Launch! ──────────────────────────────────────────────────
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "   Starting Spring Boot Application...      " -ForegroundColor Cyan
Write-Host "   http://localhost:8080/swagger-ui.html    " -ForegroundColor Cyan
Write-Host "   Press Ctrl+C to stop                    " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# Open browser after 18 seconds (app startup time)
$browserJob = Start-Job -ScriptBlock {
    Start-Sleep -Seconds 18
    Start-Process "http://localhost:8080/swagger-ui.html"
}

# Run Maven
try {
    & $mvnCmd spring-boot:run
} finally {
    Stop-Job $browserJob -ErrorAction SilentlyContinue
    Remove-Job $browserJob -ErrorAction SilentlyContinue
}
