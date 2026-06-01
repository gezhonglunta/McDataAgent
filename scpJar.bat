@echo off

set local_file=.\data-agent-management\target\spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar
set remote_path=/appdata/dataAgent/data-agent-management
set host=190.160.13.13
set port=20030

echo 同步前端文件
ssh -p %port% root@%host% "rm -rf /appdata/dataAgent/data-agent-frontend/dist"
cd .\data-agent-frontend\
scp -P %port% -r dist root@%host%:/appdata/dataAgent/data-agent-frontend/
cd ..

if not exist %local_file% (
    echo 发布包不存在
    exit /B
)

echo 停止服务
ssh -p %port% root@%host% "cd %remote_path% && sh stop.sh"
timeout /t 60
echo 查看端口占用：
ssh -p %port% root@%host% "netstat -ntlp | grep 8065"

scp -P %port% %local_file% root@%host%:%remote_path%

echo 开启服务
ssh -p %port% root@%host% "cd %remote_path% && sh start.sh"

pause
