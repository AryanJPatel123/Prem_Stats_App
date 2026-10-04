@echo off
cd /d "%~dp0"
if not exist pl-stats.jar call build.bat || exit /b 1
start "" javaw -jar pl-stats.jar
