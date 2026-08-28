@echo off

rem set /p param=请输入发布环境：1-prod、2-beta：
set param=2
if "%param%"=="" (
    echo "请输入发布环境：1-prod、2-beta"
    exit /b 1
)
set host=190.160.13.13
set port=20030
if "%param%"=="1" (
    set remote_backend=/appdata/dataAgent/data-agent-management
) else (
    set remote_backend=/appdata/dataAgent2/
)


set local_jar=.\data-agent-management\target\data-agent.jar

if not exist %local_jar% (
    echo 发布包不存在
    exit /B
)

echo 停止服务
ssh -p %port% root@%host% "cd %remote_backend%/bin && sh stop.sh"
timeout /t 5

scp -P %port% %local_jar% root@%host%:%remote_backend%
rem scp -P %port% -r .\data-agent-management\target\lib root@%host%:%remote_backend%
scp -P %port% -r .\data-agent-management\target\config root@%host%:%remote_backend%
scp -P %port% -r .\data-agent-management\target\bin root@%host%:%remote_backend%

echo 开启服务
ssh -p %port% root@%host% "cd %remote_backend%/bin && sh start.sh"

pause
