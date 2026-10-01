@echo off
setlocal
cd /d "%~dp0"
echo ============================================
echo SpotHelper + HolyWorld - build
echo ============================================
call gradlew.bat build --no-daemon
if errorlevel 1 (
  echo.
  echo BUILD FAILED.
  pause
  exit /b 1
)
echo.
echo BUILD COMPLETE. Check build\libs\
pause
