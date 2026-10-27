@echo off
setlocal
cd /d "%~dp0"
call "%~dp0mvnw.cmd" -Prelease clean verify
exit /b %errorlevel%
