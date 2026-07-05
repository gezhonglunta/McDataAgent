-- 初始化数据文件
-- 只在表为空时插入示例数据

-- 业务知识示例数据
-- 参考 KNOWLEDGE_USAGE.md：业务名称用标准术语，描述要"讲人话"说明计算公式和过滤条件，同义词枚举所有可能叫法
INSERT IGNORE INTO `business_knowledge` (`id`, `business_term`, `description`, `synonyms`, `is_recall`, `agent_id`, `created_time`, `updated_time`, `embedding_status`) VALUES
-- 电商场景示例
(1, 'GMV', 'GMV（商品交易总额）= 订单表中所有状态为"已支付"或"已发货"的订单金额总和，不扣除退款金额。注意：状态=1（已支付）和状态=2（已发货）的订单才计入GMV，状态=0（待支付）和状态=3（已取消）的不计入。', '流水, 交易额, 全站销售额, 总成交额, 商品交易总额', 1, 1, NOW(), NOW(), 'PENDING'),
(2, '活跃用户', '活跃用户定义为在选定时间段内，登录次数大于等于1次的去重用户数。统计口径：SELECT COUNT(DISTINCT user_id) FROM user_login_log WHERE login_time >= 开始时间 AND login_time <= 结束时间。', 'DAU, WAU, MAU, 日活, 周活, 月活, 活跃人数', 1, 1, NOW(), NOW(), 'PENDING'),
(3, '复购率', '复购率 = 在选定时间段内，购买次数大于等于2次的去重用户数 / 总去重购买用户数 × 100%。购买次数以订单表中状态为"已支付"的订单为准。', '重复购买率, 回头率, 再次购买比例', 1, 1, NOW(), NOW(), 'PENDING'),
-- 销售场景示例
(4, '客单价', '客单价 = 选定时间段内的总销售金额 / 总订单数。仅统计状态为"已支付"的订单，退款订单不计入分母。', '平均订单金额, 每单平均金额, 单均金额, AOV', 1, 2, NOW(), NOW(), 'PENDING'),
(5, '转化率', '转化率 = 完成目标行为的用户数 / 总访问用户数 × 100%。对于下单转化率，目标行为是"创建订单"，分母是"访问商品详情页的去重用户数"。', '转化比例, 购买转化率, 下单转化率, CVR', 1, 2, NOW(), NOW(), 'PENDING');

-- 语义模型示例数据
-- 参考 KNOWLEDGE_USAGE.md：业务名称用口语化标准名，同义词解决问法多样性，业务描述解释枚举值和特殊逻辑
INSERT IGNORE INTO `semantic_model` (`id`, `agent_id`, `datasource_id`, `table_name`, `column_name`, `business_name`, `synonyms`, `business_description`, `column_comment`, `data_type`, `created_time`, `updated_time`, `status`) VALUES
-- 电商订单表语义模型
(1, 1, 2, 'orders', 'price2', '订单金额', '订单价格, 成交价, 销售额, 支付金额, amount, 实付金额', '订单的实际支付金额，单位为元。注意区分 price（原价）和 price2（成交价/实付价），计算GMV时应使用 price2。', '订单实付金额', 'decimal(10,2)', NOW(), NOW(), 1),
(2, 1, 2, 'orders', 'order_status', '订单状态', '状态, 订单状态码, status', '订单状态枚举：0=待支付，1=已支付/已成交，2=已发货，3=已取消，4=退款中，5=已退款。计算GMV时只统计状态为1和2的订单。', '订单状态', 'tinyint', NOW(), NOW(), 1),
(3, 1, 2, 'orders', 'user_id', '用户ID', '会员ID, 客户编号, 买家ID, uid', '下单用户的唯一标识，关联 users 表的 id 字段。', '用户ID', 'bigint', NOW(), NOW(), 1),
(4, 1, 2, 'orders', 'create_time', '下单时间', '创建时间, 订单时间, 购买时间, order_time', '订单创建时间，格式为 YYYY-MM-DD HH:mm:ss。按天统计时使用 DATE(create_time) 分组。', '订单创建时间', 'datetime', NOW(), NOW(), 1),
-- 用户表语义模型
(5, 1, 2, 'users', 'user_level', '用户等级', '会员等级, 用户级别, level, 会员等级', '用户等级枚举：0=普通用户，1=银卡会员，2=金卡会员，3=钻石会员。不同等级享受不同折扣。', '用户等级', 'tinyint', NOW(), NOW(), 1),
(6, 1, 2, 'users', 'register_time', '注册时间', '注册日期, 开户时间, 加入时间', '用户注册时间，格式为 YYYY-MM-DD HH:mm:ss。新用户定义为注册时间在30天以内的用户。', '注册时间', 'datetime', NOW(), NOW(), 1);

-- 智能体示例数据
INSERT IGNORE INTO `agent` (`id`, `name`, `description`, `avatar`, `status`, `api_key`, `api_key_enabled`, `prompt`, `category`, `admin_id`, `tags`, `create_time`, `update_time`) VALUES
(1, '中国人口GDP数据智能体', '专门处理中国人口和GDP相关数据查询分析的智能体', '/avatars/china-gdp-agent.png', 'draft', NULL, 0, '你是一个专业的数据分析助手，专门处理中国人口和GDP相关的数据查询。请根据用户的问题，生成准确的SQL查询语句。', '数据分析', 2100246635, '人口数据,GDP分析,经济统计', NOW(), NOW()),
(2, '销售数据分析智能体', '专注于销售数据分析和业务指标计算的智能体', '/avatars/sales-agent.png', 'draft', NULL, 0, '你是一个销售数据分析专家，能够帮助用户分析销售趋势、客户行为和业务指标。', '业务分析', 2100246635, '销售分析,业务指标,客户分析', NOW(), NOW()),
(3, '财务报表智能体', '专门处理财务数据和报表分析的智能体', '/avatars/finance-agent.png', 'draft', NULL, 0, '你是一个财务分析专家，专门处理财务数据查询和报表生成。', '财务分析', 2100246635, '财务数据,报表分析,会计', NOW(), NOW()),
(4, '库存管理智能体', '专注于库存数据管理和供应链分析的智能体', '/avatars/inventory-agent.png', 'draft', NULL, 0, '你是一个库存管理专家，能够帮助用户查询库存状态、分析供应链数据。', '供应链', 2100246635, '库存管理,供应链,物流', NOW(), NOW());

-- 智能体知识示例数据
-- 参考 KNOWLEDGE_USAGE.md：文档用于SOP/Schema说明，Q&A用于纠正Agent错误行为（Few-Shot Learning）
INSERT IGNORE INTO `agent_knowledge` (`id`, `agent_id`, `title`, `content`, `type`, `is_recall`, `embedding_status`, `file_type`, `question`, `created_time`, `updated_time`) VALUES
-- 文档类型：数据库Schema说明书
(1, 1, '中国人口统计数据表结构说明', '中国人口统计数据包含以下核心表：\n1. population（人口总数表）：字段包括 year(年份)、total_population(总人口数)、male_population(男性人口)、female_population(女性人口)、urban_population(城镇人口)、rural_population(农村人口)\n2. population_age（年龄结构表）：字段包括 year(年份)、age_group(年龄组，如0-14岁、15-64岁、65岁以上)、population(该年龄组人口数)、ratio(占比%)\n3. gdp_data（GDP数据表）：字段包括 year(年份)、quarter(季度，1-4或0表示年度)、nominal_gdp(名义GDP，亿元)、real_gdp(实际GDP，亿元)、growth_rate(同比增长率%)\n\n注意：gdp_data 表中 quarter=0 表示年度数据，1-4 表示各季度数据。查询年度GDP时应加条件 quarter=0。', 'DOCUMENT', 1, 'PENDING', 'text', NULL, NOW(), NOW()),
-- 文档类型：业务流程SOP
(2, 1, '人口趋势分析标准流程', '进行人口趋势分析时，建议按以下步骤：\n1. 首先确定分析的时间范围（如近10年、近5年）\n2. 查询总人口变化趋势：SELECT year, total_population FROM population WHERE year >= 起始年 ORDER BY year\n3. 计算年增长率：(当年人口 - 上年人口) / 上年人口 × 100%\n4. 分析年龄结构变化：重点关注65岁以上人口占比是否超过7%（老龄化标准）\n5. 分析城乡结构变化：城镇人口占比趋势\n6. 结合GDP数据分析人均GDP变化\n7. 生成分析报告时应包含：趋势图、关键指标对比、结论建议', 'DOCUMENT', 1, 'PENDING', 'text', NULL, NOW(), NOW()),
-- Q&A类型：纠正Agent常见错误（Few-Shot Learning）
(3, 1, 'GDP增长率计算方式', 'SELECT (a.nominal_gdp - b.nominal_gdp) / b.nominal_gdp * 100 AS growth_rate\nFROM gdp_data a\nJOIN gdp_data b ON a.year = b.year + 1\nWHERE a.quarter = 0 AND b.quarter = 0\nORDER BY a.year', 'QA', 1, 'PENDING', 'text', '如何计算GDP的年度同比增长率？', NOW(), NOW()),
(4, 1, '查询特定年份人口数据', 'SELECT * FROM population WHERE year = 目标年份。注意：population 表中每年只有一条记录，不需要 GROUP BY。如果需要查询多年趋势，使用 WHERE year BETWEEN 起始年 AND 结束年。', 'QA', 1, 'PENDING', 'text', '如何查询2023年的人口数据？', NOW(), NOW()),
-- 销售智能体知识
(5, 2, '销售数据分析指标体系', '销售数据分析包含以下核心指标：\n1. GMV（商品交易总额）：状态为"已支付"或"已发货"的订单金额总和\n2. 客单价：总销售金额 / 总订单数\n3. 转化率：下单用户数 / 访问用户数\n4. 复购率：购买≥2次的用户数 / 总购买用户数\n5. 退货率：退货订单数 / 总订单数\n\n数据表关联关系：\n- orders.user_id = users.id（订单关联用户）\n- orders.product_id = products.id（订单关联商品）\n- orders.region = regions.id（订单关联区域）', 'DOCUMENT', 1, 'PENDING', 'text', NULL, NOW(), NOW()),
(6, 2, '销售额查询正确写法', 'SELECT DATE(create_time) AS sale_date, SUM(price2) AS total_amount, COUNT(*) AS order_count\nFROM orders\nWHERE order_status IN (1, 2)\n  AND create_time >= ''2024-01-01''\n  AND create_time < ''2024-02-01''\nGROUP BY DATE(create_time)\nORDER BY sale_date\n\n注意：\n1. 必须过滤 order_status IN (1, 2)，排除待支付和已取消的订单\n2. 使用 price2（实付金额）而非 price（原价）\n3. 时间范围用 >= 和 < 避免边界问题', 'QA', 1, 'PENDING', 'text', '统计2024年1月的每日销售额', NOW(), NOW()),
(7, 2, '高价值用户定义', '高价值用户（VIP用户）的定义：\n1. 累计消费金额 ≥ 10000元\n2. 或者累计订单数 ≥ 10单\n3. 或者用户等级为金卡(2)或钻石会员(3)\n\nSQL判断逻辑：\nSELECT u.*, \n  COALESCE(SUM(o.price2), 0) AS total_spent,\n  COUNT(o.id) AS order_count\nFROM users u\nLEFT JOIN orders o ON u.id = o.user_id AND o.order_status IN (1, 2)\nGROUP BY u.id\nHAVING total_spent >= 10000 OR order_count >= 10 OR u.user_level IN (2, 3)', 'FAQ', 1, 'PENDING', 'text', '什么是高价值用户？如何筛选？', NOW(), NOW());

-- 数据源示例数据
-- 示例数据源可以运行docker-compose-datasource.yml建立，或者手动修改为自己的数据源
INSERT IGNORE INTO `datasource` (`id`, `name`, `type`, `host`, `port`, `database_name`, `username`, `password`, `connection_url`, `status`, `test_status`, `description`, `creator_id`, `create_time`, `update_time`) VALUES 
(1, '生产环境MySQL数据库', 'mysql', 'mysql-data', 3306, 'product_db', 'root', 'root', 'jdbc:mysql://mysql-data:3306/product_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true', 'inactive', 'unknown', '生产环境主数据库，包含核心业务数据', 2100246635, NOW(), NOW()),
(2, '数据仓库PostgreSQL', 'postgresql', 'postgres-data', 5432, 'data_warehouse', 'postgres', 'postgres', 'jdbc:postgresql://postgres-data:5432/data_warehouse', 'inactive', 'unknown', '数据仓库，用于数据分析和报表生成', 2100246635, NOW(), NOW());

-- 智能体数据源关联示例数据
INSERT IGNORE INTO `agent_datasource` (`id`, `agent_id`, `datasource_id`, `is_active`, `create_time`, `update_time`) VALUES 
(1, 1, 2, 0, NOW(), NOW()),  -- 中国人口GDP数据智能体使用数据仓库
(2, 2, 1, 0, NOW(), NOW()),  -- 销售数据分析智能体使用生产环境数据库
(3, 3, 1, 0, NOW(), NOW()),  -- 财务报表智能体使用生产环境数据库
(4, 4, 1, 0, NOW(), NOW());  -- 库存管理智能体使用生产环境数据库
