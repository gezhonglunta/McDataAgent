@echo off
if exist ".\data-agent-frontend\" (
    cd .\data-agent-frontend
)
call npm install
set VITE_BACKEND_TARGET=http://190.160.13.13:20037
call npm run dev
pause
