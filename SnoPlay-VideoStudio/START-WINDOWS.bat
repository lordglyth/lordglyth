@echo off
title SnoPlay Video Studio
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (
  echo Python launcher not found. Install Python 3.10 or newer.
  pause
  exit /b 1
)
where ffmpeg >nul 2>nul
if errorlevel 1 (
  echo FFmpeg is missing. Install it with: winget install Gyan.FFmpeg
  echo Then close and reopen this window.
  pause
  exit /b 1
)
if not exist ".venv\Scripts\python.exe" (
  echo Preparing private SnoPlay Python environment...
  py -m venv .venv
  if errorlevel 1 goto failed
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt
  if errorlevel 1 goto failed
)
start "" cmd /c "timeout /t 4 >nul && start http://127.0.0.1:8765"
echo.
echo SnoPlay Video Studio is opening at http://127.0.0.1:8765
echo Press Ctrl+C to stop the local server.
echo.
".venv\Scripts\python.exe" server.py
goto done
:failed
echo Installation did not finish. Check Python and your internet connection.
:done
pause
