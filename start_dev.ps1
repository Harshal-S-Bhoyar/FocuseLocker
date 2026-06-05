# ============================================================
# FocuseLocker Dev Startup Script
# Run this ONCE every time you connect your phone via USB.
# It sets up ADB tunnel + backend + installs debug APK.
# ============================================================

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$backendDir  = Join-Path $projectRoot "backend"

Write-Host "=== FocuseLocker Dev Startup ===" -ForegroundColor Cyan

# ── STEP 1: Verify device is connected ──────────────────────
Write-Host "`n[1/4] Checking ADB device..." -ForegroundColor Yellow
$devices = adb devices 2>&1
if ($devices -notmatch "device$") {
    Write-Host "ERROR: No Android device found. Connect phone via USB and enable USB Debugging." -ForegroundColor Red
    exit 1
}
Write-Host "Device found." -ForegroundColor Green

# ── STEP 2: Re-establish ADB reverse tunnel ──────────────────
# This is the PRIMARY cause of "Failed to connect to server".
# The tunnel dies whenever ADB restarts or USB is disconnected.
Write-Host "`n[2/4] Setting up ADB reverse tunnel (port 3000)..." -ForegroundColor Yellow
adb reverse tcp:3000 tcp:3000
$tunnelCheck = adb reverse --list
if ($tunnelCheck -match "tcp:3000") {
    Write-Host "Tunnel active: $tunnelCheck" -ForegroundColor Green
} else {
    Write-Host "ERROR: ADB tunnel failed to establish. Check USB connection." -ForegroundColor Red
    exit 1
}

# ── STEP 3: Start backend server ────────────────────────────
Write-Host "`n[3/4] Starting backend server on port 3000..." -ForegroundColor Yellow

# Check if already running on port 3000
$portCheck = netstat -ano 2>&1 | Select-String ":3000"
if ($portCheck) {
    Write-Host "Backend already running on port 3000 (OK)." -ForegroundColor Green
} else {
    Write-Host "Starting Node.js backend..." -ForegroundColor Yellow
    # Start backend in a new window so this script stays free
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendDir'; node dist/server.js" -WindowStyle Normal
    Start-Sleep -Seconds 2

    # Verify it started
    $portCheck2 = netstat -ano 2>&1 | Select-String ":3000"
    if ($portCheck2) {
        Write-Host "Backend started OK." -ForegroundColor Green
    } else {
        Write-Host "WARNING: Backend may not have started. Check the new terminal window." -ForegroundColor Yellow
    }
}

# ── STEP 4: Quick connectivity test ─────────────────────────
Write-Host "`n[4/4] Testing tunnel connectivity from device..." -ForegroundColor Yellow
$curlTest = adb shell "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/api/verify-contact --max-time 5" 2>&1
if ($curlTest -match "40[0-9]|200") {
    Write-Host "Connectivity OK! Server responded: HTTP $curlTest" -ForegroundColor Green
} else {
    Write-Host "WARNING: No HTTP response from device. Raw output: $curlTest" -ForegroundColor Yellow
    Write-Host "Try running the app anyway — the tunnel should still work." -ForegroundColor Yellow
}

Write-Host "`n=== Setup Complete ===" -ForegroundColor Cyan
Write-Host "Now run:  .\gradlew installDebug" -ForegroundColor White
Write-Host "Then open FocuseLocker on your phone and test OTP." -ForegroundColor White
