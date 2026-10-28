@echo off
echo ==========================================
echo  Stopping Reader (all services)
echo ==========================================
echo.

taskkill /F /IM weed.exe /T 2>nul && echo  [OK] SeaweedFS stopped || echo  [--] SeaweedFS not running

taskkill /F /IM cloudflared.exe /T 2>nul && echo  [OK] cloudflared stopped || echo  [--] cloudflared not running

echo.
echo  Stopping backend (java on port 8080)...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%p /T 2>nul && echo  [OK] Backend stopped (PID %%p)
)

echo.
echo ==========================================
echo  All services stopped.
echo ==========================================
pause
