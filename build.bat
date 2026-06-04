cd data-agent-frontend
call npm run build
cd ..
call mvn clean package -Dmaven.test.skip=true
pause