@echo off

setlocal
set "BASE_DIR=%~dp0.."
cd /d "%BASE_DIR%"

set SERVER_PORT=8780
if not defined JAVA_OPTS set "JAVA_OPTS=-Xms512m -Xmx4g -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

if not exist logs mkdir logs
set "LOG_FILE=logs\start.log"

start "" /b java %JAVA_OPTS% -Dloader.path=lib -Dspring.config.additional-location=optional:file:./config/ -Dlogging.config=file:./config/logback-spring.xml -jar data-agent.jar --server.port=%SERVER_PORT% %* > "%LOG_FILE%" 2>&1

echo DataAgent Start Log: %BASE_DIR%\%LOG_FILE%

endlocal
