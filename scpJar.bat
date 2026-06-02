@echo off

set host=190.160.13.13
set port=20030
set remote_base=/appdata/dataAgent
set remote_frontend=%remote_base%/data-agent-frontend
set remote_backend=%remote_base%/data-agent-management
set local_frontend=.\data-agent-frontend
set local_frontend_dist=%local_frontend%\dist
set local_jar=.\data-agent-management\target\spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar

if exist %local_frontend_dist% (
    echo 同步前端文件
    ssh -p %port% root@%host% "rm -rf %remote_frontend%/dist"
    scp -P %port% -r %local_frontend_dist% root@%host%:%remote_frontend%/
) else (
    echo 前端dist目录不存在，跳过同步前端文件
)

if not exist %local_jar% (
    echo 发布包不存在
    exit /B
)

echo 停止服务
ssh -p %port% root@%host% "cd %remote_backend% && sh stop.sh"
timeout /t 60
echo 查看端口占用：
ssh -p %port% root@%host% "netstat -ntlp | grep 8065"

scp -P %port% %local_jar% root@%host%:%remote_backend%

echo 开启服务
ssh -p %port% root@%host% "cd %remote_backend% && sh start.sh"

pause
