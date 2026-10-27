@echo off
setlocal
cd /d "%~dp0"
if not exist "%~dp0server\target\tiwanaku.jar" (
  echo Game JAR not found. Run build-fast.cmd or build.cmd first.
  exit /b 1
)
java -jar "%~dp0server\target\tiwanaku.jar" %*
exit /b %errorlevel%
