@echo off
if exist ".\data-agent-frontend\" (
    cd .\data-agent-frontend
)
call npm install
call npm run dev
pause
