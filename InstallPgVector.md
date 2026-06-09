# 安装插件
```bash
cd /tmp
git clone --branch v0.8.2 https://github.com/pgvector/pgvector.git
cd pgvector
su - postgres
su root -c "make"
su root -c "make install"

pg_ctl stop
pg_ctl start

psql
```

# 创建数据库、账户、模式
```sql
-- create tablespace tbs_dataagentdb location '/appdata/postgres/data/dataagentdb/';
create database dataagentdb with template template0 lc_collate 'zh_CN.utf8' lc_ctype 'zh_CN.utf8' encoding='UTF8';
CREATE USER dataagent WITH superuser ENCRYPTED PASSWORD 'a123456.';
GRANT ALL PRIVILEGES ON DATABASE dataagentdb TO dataagent;
\c dataagentdb
create schema dataagent;
grant all on schema dataagent to dataagent;

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
create EXTENSION IF NOT EXISTS pg_stat_statements;
```

# 历史数据迁移
```
mysqldump -h190.160.13.13 -P20032 -udataAgent -pa123456. --compatible=ansi --no-create-info --no-tablespaces --skip-add-locks --skip-disable-keys --skip-set-charset --skip-comments --default-character-set=utf8mb4 saa_data_agent > D:\tmp\saa_data_standard_data.sql
```