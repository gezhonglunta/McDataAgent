@echo off

rem DataAgent 后端启动脚本（Windows）
rem 目录结构:
rem   bin\start.bat  本脚本
rem   data-agent.jar 核心 jar（不含依赖）
rem   lib\           运行时依赖 jar
rem   config\        外部配置（application.yml、logback-spring.xml 等）

setlocal
set "BASE_DIR=%~dp0.."
cd /d "%BASE_DIR%"

if not defined JAVA_OPTS set "JAVA_OPTS=-Xms512m -Xmx4g -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

java %JAVA_OPTS% -Dloader.path=lib -Dspring.config.additional-location=optional:file:./config/ -jar data-agent.jar %*

endlocal