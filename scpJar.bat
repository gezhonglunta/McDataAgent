@echo off

set /p param=请输入发布环境：1-prod、2-beta：
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


set local_jar=.\data-agent-management\target\spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar

if not exist %local_jar% (
    echo 发布包不存在
    exit /B
)

echo 停止服务
ssh -p %port% root@%host% "cd %remote_backend% && sh stop.sh"
timeout /t 30

scp -P %port% %local_jar% root@%host%:%remote_backend%

echo 开启服务
ssh -p %port% root@%host% "cd %remote_backend% && sh start_sso.sh"

pause
