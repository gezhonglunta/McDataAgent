cd data-agent-frontend-nuxt
call pnpm build
cd ..
rd /s /q .\data-agent-management\src\main\resources\static\front
xcopy /e /y .\data-agent-frontend-nuxt\.output\public .\data-agent-management\src\main\resources\static\front\
call mvn clean package -Dmaven.test.skip=true
pause