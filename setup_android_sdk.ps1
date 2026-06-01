# ==============================================================================
# FocusLock - Android SDK Command-Line Setup Script
# ==============================================================================
# This script automates downloading the official Android command-line tools,
# extracting them to your custom directory, creating the correct folder layout,
# and permanently registering environment variables for your Windows User.
# ==============================================================================

# Force script to stop on any unexpected errors
$ErrorActionPreference = "Stop"

# Define Paths and URLs
$sdkRoot = "C:\Users\harshal.bhoyar\AndroidSDK"
$downloadUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
$tempZipPath = Join-Path $env:TEMP "android_cmdline_tools.zip"

Write-Host "=== Starting Android SDK Command Line Setup ===" -ForegroundColor Cyan

# 1. Create target SDK directories if they do not exist
try {
    if (-not (Test-Path $sdkRoot)) {
        Write-Host "Creating SDK Root directory at: $sdkRoot" -ForegroundColor Green
        New-Item -Path $sdkRoot -ItemType Directory | Out-Null
    }
} catch {
    Write-Error "Failed to create target directory '$sdkRoot'. Details: $_"
}

# 2. Download the official Android Command Line Tools ZIP
try {
    Write-Host "Downloading Android command-line tools..." -ForegroundColor Green
    Write-Host "Source: $downloadUrl" -ForegroundColor Gray
    Invoke-WebRequest -Uri $downloadUrl -OutFile $tempZipPath -UseBasicParsing
    Write-Host "Download complete. Saved to: $tempZipPath" -ForegroundColor Green
} catch {
    Write-Error "Failed to download command-line tools. Please check your internet connection. Details: $_"
}

# 3. Extract the ZIP and arrange the required directory structure
# Crucial Android SDK detail: sdkmanager EXPECTS to reside in a folder path structure:
# `<sdk_root>/cmdline-tools/latest/bin/sdkmanager.bat`
try {
    Write-Host "Extracting command-line tools..." -ForegroundColor Green
    
    # Define paths for folder structuring
    $cmdlineToolsDir = Join-Path $sdkRoot "cmdline-tools"
    $latestToolsDir = Join-Path $cmdlineToolsDir "latest"

    # Clean up existing cmdline-tools directory if present to avoid merging errors
    if (Test-Path $cmdlineToolsDir) {
        Write-Host "Cleaning up old cmdline-tools directory..." -ForegroundColor Yellow
        Remove-Item -Path $cmdlineToolsDir -Recurse -Force | Out-Null
    }

    # Extract to a temporary extraction path
    $extractPath = Join-Path $env:TEMP "android_extract"
    if (Test-Path $extractPath) {
        Remove-Item -Path $extractPath -Recurse -Force | Out-Null
    }
    New-Item -Path $extractPath -ItemType Directory | Out-Null
    
    Expand-Archive -Path $tempZipPath -DestinationPath $extractPath

    # Move files to build the required: sdk_root/cmdline-tools/latest/ folder structure
    New-Item -Path $latestToolsDir -ItemType Directory -Force | Out-Null
    
    # The ZIP extracts a folder named 'cmdline-tools' containing 'bin', 'lib', etc.
    $sourceFilesDir = Join-Path $extractPath "cmdline-tools"
    
    Write-Host "Structuring files to match sdkmanager requirements..." -ForegroundColor Green
    Move-Item -Path "$sourceFilesDir\*" -Destination $latestToolsDir -Force

    # Clean up temporary zip and extraction folders
    Remove-Item -Path $tempZipPath -Force | Out-Null
    Remove-Item -Path $extractPath -Recurse -Force | Out-Null
    
    Write-Host "Command-line tools successfully installed at: $latestToolsDir" -ForegroundColor Green
} catch {
    Write-Error "Failed during extraction and directory structuring. Details: $_"
}

# 4. Permanently register User Environment Variables
try {
    Write-Host "Configuring Windows Environment Variables..." -ForegroundColor Green

    # Set ANDROID_HOME for current user
    [System.Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkRoot, [System.EnvironmentVariableTarget]::User)
    Write-Host "ANDROID_HOME has been set to: $sdkRoot" -ForegroundColor Green

    # Retrieve current User PATH
    $userPath = [System.Environment]::GetEnvironmentVariable("PATH", [System.EnvironmentVariableTarget]::User)
    
    # Define new paths to append to the system user path
    $binPath = "$sdkRoot\cmdline-tools\latest\bin"
    $platformToolsPath = "$sdkRoot\platform-tools"

    # Append paths if they are not already present in the PATH environment variable
    $updatedPath = $userPath
    if ($updatedPath -notlike "*$binPath*") {
        $updatedPath = $updatedPath + ";" + $binPath
    }
    if ($updatedPath -notlike "*$platformToolsPath*") {
        $updatedPath = $updatedPath + ";" + $platformToolsPath
    }

    # Save the updated User PATH environment variable
    [System.Environment]::SetEnvironmentVariable("PATH", $updatedPath, [System.EnvironmentVariableTarget]::User)
    Write-Host "Environment variables successfully added to PATH." -ForegroundColor Green
    Write-Host "Added path: $binPath" -ForegroundColor Gray
    Write-Host "Added path: $platformToolsPath" -ForegroundColor Gray
} catch {
    Write-Error "Failed to set environment variables. Details: $_"
}

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Step 1, 2, and 3 Completed Successfully!" -ForegroundColor Green
Write-Host "Please close this terminal and open a NEW terminal window for PATH changes to take effect." -ForegroundColor Yellow
Write-Host "==============================================================================" -ForegroundColor Cyan
