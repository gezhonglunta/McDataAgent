cd data-agent-frontend-nuxt
call pnpm build
cd ..
call mvn clean package -Dmaven.test.skip=true
pause