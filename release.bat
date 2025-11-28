@echo off
REM Release script for Mohican (Windows)
REM Usage: release.bat <version>
REM Example: release.bat 1.11
REM
REM Prerequisites:
REM - Set GITHUB_TOKEN environment variable with a GitHub personal access token
REM   that has 'repo' permissions

setlocal enabledelayedexpansion

if "%~1"=="" (
    echo Usage: %0 ^<version^>
    echo Example: %0 1.11
    exit /b 1
)

set VERSION=%~1

REM Calculate next version (simple increment of minor version)
for /f "tokens=1,2 delims=." %%a in ("%VERSION%") do (
    set /a NEXT_MINOR=%%b+1
    set NEXT_VERSION=%%a.!NEXT_MINOR!
)

if "%GITHUB_TOKEN%"=="" (
    echo Error: GITHUB_TOKEN environment variable is not set
    echo Please set it with: set GITHUB_TOKEN=your_token
    exit /b 1
)

echo === Mohican Release Script ===
echo Release version: %VERSION%
echo Next dev version: %NEXT_VERSION%-SNAPSHOT
echo.

set /p CONFIRM="Continue? (y/n) "
if /i not "%CONFIRM%"=="y" exit /b 1

echo Setting version to %VERSION%...
call mvn versions:set -DnewVersion=%VERSION% -DgenerateBackupPoms=false
if errorlevel 1 exit /b 1

echo Building...
call mvn clean package -DskipTests
if errorlevel 1 exit /b 1

echo Creating git commit and tag...
git add pom.xml
git commit -m "Release version %VERSION%"
git tag -a "v%VERSION%" -m "Release %VERSION%"

echo Pushing tag...
git push origin "v%VERSION%"

echo Uploading to GitHub Releases...
call mvn -Prelease deploy -DskipTests
if errorlevel 1 exit /b 1

echo Setting next development version to %NEXT_VERSION%-SNAPSHOT...
call mvn versions:set -DnewVersion=%NEXT_VERSION%-SNAPSHOT -DgenerateBackupPoms=false
git add pom.xml
git commit -m "Prepare next development version %NEXT_VERSION%-SNAPSHOT"
git push origin main

echo.
echo === Release %VERSION% completed! ===
echo GitHub Release: https://github.com/mcix/mohican/releases/tag/v%VERSION%
