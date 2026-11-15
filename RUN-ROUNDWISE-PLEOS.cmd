@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0pleos-ui\run.ps1"
if errorlevel 1 pause
