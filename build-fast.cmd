@echo off
setlocal
cd /d "%~dp0"
set "TIWANAKU_SKIP_FRONTEND_TESTS=1"
call "%~dp0mvnw.cmd" -Prelease clean package -DskipTests
exit /b %errorlevel%
