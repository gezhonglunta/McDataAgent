-- 简化的数据库初始化脚本，适配PostgreSQL
-- 智能体表
CREATE TABLE IF NOT EXISTS agent (
    id SERIAL NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    avatar TEXT,
    status VARCHAR(50) DEFAULT 'draft',
    api_key VARCHAR(255) DEFAULT NULL,
    api_key_enabled SMALLINT DEFAULT 0,
    prompt TEXT,
    category VARCHAR(100),
    admin_id BIGINT,
    tags TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
-- 智能体表字段注释
COMMENT ON TABLE agent IS '智能体表';
COMMENT ON COLUMN agent.id IS '';
COMMENT ON COLUMN agent.name IS '智能体名称';
COMMENT ON COLUMN agent.description IS '智能体描述';
COMMENT ON COLUMN agent.avatar IS '头像URL';
COMMENT ON COLUMN agent.status IS '状态：draft-待发布，published-已发布，offline-已下线';
COMMENT ON COLUMN agent.api_key IS '访问 API Key，格式 sk-xxx';
COMMENT ON COLUMN agent.api_key_enabled IS 'API Key 是否启用：0-禁用，1-启用';
COMMENT ON COLUMN agent.prompt IS '自定义Prompt配置';
COMMENT ON COLUMN agent.category IS '分类';
COMMENT ON COLUMN agent.admin_id IS '管理员ID';
COMMENT ON COLUMN agent.tags IS '标签，逗号分隔';
COMMENT ON COLUMN agent.create_time IS '创建时间';
COMMENT ON COLUMN agent.update_time IS '更新时间';
-- 智能体表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_agent_name ON agent(name);
CREATE INDEX IF NOT EXISTS idx_agent_status ON agent(status);
CREATE INDEX IF NOT EXISTS idx_agent_category ON agent(category);
CREATE INDEX IF NOT EXISTS idx_agent_admin_id ON agent(admin_id);

-- 业务知识表
CREATE TABLE IF NOT EXISTS business_knowledge (
  id SERIAL NOT NULL,
  business_term VARCHAR(255) NOT NULL,
  description TEXT,
  synonyms TEXT,
  is_recall INT DEFAULT 1,
  agent_id INT NOT NULL,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  embedding_status VARCHAR(20) DEFAULT NULL,
  error_msg VARCHAR(255) DEFAULT NULL,
  is_deleted INT DEFAULT 0,
  PRIMARY KEY (id),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
-- 业务知识表字段注释
COMMENT ON TABLE business_knowledge IS '业务知识表';
COMMENT ON COLUMN business_knowledge.id IS '';
COMMENT ON COLUMN business_knowledge.business_term IS '业务名词';
COMMENT ON COLUMN business_knowledge.description IS '描述';
COMMENT ON COLUMN business_knowledge.synonyms IS '同义词，逗号分隔';
COMMENT ON COLUMN business_knowledge.is_recall IS '是否召回：0-不召回，1-召回';
COMMENT ON COLUMN business_knowledge.agent_id IS '关联的智能体ID';
COMMENT ON COLUMN business_knowledge.created_time IS '创建时间';
COMMENT ON COLUMN business_knowledge.updated_time IS '更新时间';
COMMENT ON COLUMN business_knowledge.embedding_status IS '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败';
COMMENT ON COLUMN business_knowledge.error_msg IS '操作失败的错误信息';
COMMENT ON COLUMN business_knowledge.is_deleted IS '逻辑删除：0-未删除，1-已删除';
-- 业务知识表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_business_knowledge_term ON business_knowledge(business_term);
CREATE INDEX IF NOT EXISTS idx_business_knowledge_agent_id ON business_knowledge(agent_id);
CREATE INDEX IF NOT EXISTS idx_business_knowledge_is_recall ON business_knowledge(is_recall);
CREATE INDEX IF NOT EXISTS idx_business_knowledge_embedding_status ON business_knowledge(embedding_status);
CREATE INDEX IF NOT EXISTS idx_business_knowledge_is_deleted ON business_knowledge(is_deleted);

-- 语义模型表
CREATE TABLE IF NOT EXISTS semantic_model (
  id SERIAL NOT NULL,
  agent_id INT NOT NULL,
  datasource_id INT NOT NULL,
  table_name VARCHAR(255) NOT NULL,
  column_name VARCHAR(255) NOT NULL DEFAULT '',
  business_name VARCHAR(255) NOT NULL DEFAULT '',
  synonyms TEXT,
  business_description TEXT,
  column_comment VARCHAR(255) DEFAULT NULL,
  data_type VARCHAR(255) NOT NULL DEFAULT '',
  status SMALLINT NOT NULL DEFAULT '1',
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_semantic_model_agent FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
-- 语义模型表字段注释
COMMENT ON TABLE semantic_model IS '语义模型表';
COMMENT ON COLUMN semantic_model.id IS '';
COMMENT ON COLUMN semantic_model.agent_id IS '关联的智能体ID';
COMMENT ON COLUMN semantic_model.datasource_id IS '关联的数据源ID';
COMMENT ON COLUMN semantic_model.table_name IS '关联的表名';
COMMENT ON COLUMN semantic_model.column_name IS '数据库中的物理字段名 (例如: csat_score)';
COMMENT ON COLUMN semantic_model.business_name IS '业务名/别名 (例如: 客户满意度分数)';
COMMENT ON COLUMN semantic_model.synonyms IS '业务名的同义词 (例如: 满意度,客户评分)';
COMMENT ON COLUMN semantic_model.business_description IS '业务描述 (用于向LLM解释字段的业务含义)';
COMMENT ON COLUMN semantic_model.column_comment IS '数据库中的物理字段的原始注释 ';
COMMENT ON COLUMN semantic_model.data_type IS '物理数据类型 (例如: int, varchar(20))';
COMMENT ON COLUMN semantic_model.status IS '0 停用 1 启用';
COMMENT ON COLUMN semantic_model.created_time IS '创建时间';
COMMENT ON COLUMN semantic_model.updated_time IS '更新时间';
-- 语义模型表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_semantic_model_agent_id ON semantic_model(agent_id);
CREATE INDEX IF NOT EXISTS idx_semantic_model_business_name ON semantic_model(business_name);
CREATE INDEX IF NOT EXISTS idx_semantic_model_status ON semantic_model(status);

-- 智能体知识表
CREATE TABLE IF NOT EXISTS agent_knowledge (
  id SERIAL NOT NULL,
  agent_id INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  question TEXT,
  content MEDIUMTEXT,
  is_recall INT DEFAULT 1,
  embedding_status VARCHAR(20) DEFAULT NULL,
  error_msg VARCHAR(255) DEFAULT NULL,
  source_filename VARCHAR(500) DEFAULT NULL,
  file_path VARCHAR(500) DEFAULT NULL,
  file_size BIGINT DEFAULT NULL,
  file_type VARCHAR(255) DEFAULT NULL,
  splitter_type VARCHAR(50) DEFAULT 'token',
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted INT DEFAULT 0,
  is_resource_cleaned INT DEFAULT 0,
  PRIMARY KEY (id)
);
-- 智能体知识表字段注释
COMMENT ON TABLE agent_knowledge IS '智能体知识源管理表 (支持文档、QA、FAQ)';
COMMENT ON COLUMN agent_knowledge.id IS '主键ID, 用于内部关联';
COMMENT ON COLUMN agent_knowledge.agent_id IS '关联的智能体ID';
COMMENT ON COLUMN agent_knowledge.title IS '知识的标题 (用户定义, 用于在UI上展示和识别)';
COMMENT ON COLUMN agent_knowledge.type IS '知识类型: DOCUMENT-文档, QA-问答, FAQ-常见问题';
COMMENT ON COLUMN agent_knowledge.question IS '问题 (仅当type为QA或FAQ时使用)';
COMMENT ON COLUMN agent_knowledge.content IS '知识内容 (对于QA/FAQ是答案; 对于DOCUMENT, 此字段通常为空)';
COMMENT ON COLUMN agent_knowledge.is_recall IS '业务状态: 1=召回, 0=非召回';
COMMENT ON COLUMN agent_knowledge.embedding_status IS '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败';
COMMENT ON COLUMN agent_knowledge.error_msg IS '操作失败的错误信息';
COMMENT ON COLUMN agent_knowledge.source_filename IS '上传时的原始文件名';
COMMENT ON COLUMN agent_knowledge.file_path IS '文件在服务器上的物理存储路径';
COMMENT ON COLUMN agent_knowledge.file_size IS '文件大小 (字节)';
COMMENT ON COLUMN agent_knowledge.file_type IS '文件类型（pdf,md,markdown,doc等）';
COMMENT ON COLUMN agent_knowledge.splitter_type IS '分块策略类型：token, recursive, sentence, semantic';
COMMENT ON COLUMN agent_knowledge.created_time IS '创建时间';
COMMENT ON COLUMN agent_knowledge.updated_time IS '更新时间';
COMMENT ON COLUMN agent_knowledge.is_deleted IS '逻辑删除字段，0=未删除, 1=已删除';
COMMENT ON COLUMN agent_knowledge.is_resource_cleaned IS '0=物理资源（文件和向量）未清理, 1=物理资源已清理';
-- 智能体知识表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_agent_knowledge_agent_recall ON agent_knowledge(agent_id, is_recall);
CREATE INDEX IF NOT EXISTS idx_agent_knowledge_embedding_status ON agent_knowledge(embedding_status);
CREATE INDEX IF NOT EXISTS idx_agent_knowledge_is_deleted ON agent_knowledge(is_deleted);

-- 数据源表
CREATE TABLE IF NOT EXISTS datasource (
  id SERIAL NOT NULL,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  host VARCHAR(255) NOT NULL,
  port INT NOT NULL,
  database_name VARCHAR(255) NOT NULL,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  connection_url VARCHAR(1000),
  status VARCHAR(50) DEFAULT 'inactive',
  test_status VARCHAR(50) DEFAULT 'unknown',
  description TEXT,
  creator_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
-- 数据源表字段注释
COMMENT ON TABLE datasource IS '数据源表';
COMMENT ON COLUMN datasource.id IS '';
COMMENT ON COLUMN datasource.name IS '数据源名称';
COMMENT ON COLUMN datasource.type IS '数据源类型：mysql, postgresql';
COMMENT ON COLUMN datasource.host IS '主机地址';
COMMENT ON COLUMN datasource.port IS '端口号';
COMMENT ON COLUMN datasource.database_name IS '数据库名称';
COMMENT ON COLUMN datasource.username IS '用户名';
COMMENT ON COLUMN datasource.password IS '密码（加密存储）';
COMMENT ON COLUMN datasource.connection_url IS '完整连接URL';
COMMENT ON COLUMN datasource.status IS '状态：active-启用，inactive-禁用';
COMMENT ON COLUMN datasource.test_status IS '连接测试状态：success-成功，failed-失败，unknown-未知';
COMMENT ON COLUMN datasource.description IS '描述';
COMMENT ON COLUMN datasource.creator_id IS '创建者ID';
COMMENT ON COLUMN datasource.create_time IS '创建时间';
COMMENT ON COLUMN datasource.update_time IS '更新时间';
-- 数据源表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_datasource_name ON datasource(name);
CREATE INDEX IF NOT EXISTS idx_datasource_type ON datasource(type);
CREATE INDEX IF NOT EXISTS idx_datasource_status ON datasource(status);
CREATE INDEX IF NOT EXISTS idx_datasource_creator_id ON datasource(creator_id);

-- 逻辑外键配置表
CREATE TABLE IF NOT EXISTS logical_relation (
  id SERIAL NOT NULL,
  datasource_id INT NOT NULL,
  source_table_name VARCHAR(100) NOT NULL,
  source_column_name VARCHAR(100) NOT NULL,
  target_table_name VARCHAR(100) NOT NULL,
  target_column_name VARCHAR(100) NOT NULL,
  relation_type VARCHAR(20) DEFAULT NULL,
  description VARCHAR(500) DEFAULT NULL,
  is_deleted SMALLINT DEFAULT 0,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
);
-- 逻辑外键配置表字段注释
COMMENT ON TABLE logical_relation IS '逻辑外键配置表';
COMMENT ON COLUMN logical_relation.id IS '主键ID';
COMMENT ON COLUMN logical_relation.datasource_id IS '关联的数据源ID';
COMMENT ON COLUMN logical_relation.source_table_name IS '主表名 (例如 t_order)';
COMMENT ON COLUMN logical_relation.source_column_name IS '主表字段名 (例如 buyer_uid)';
COMMENT ON COLUMN logical_relation.target_table_name IS '关联表名 (例如 t_user)';
COMMENT ON COLUMN logical_relation.target_column_name IS '关联表字段名 (例如 id)';
COMMENT ON COLUMN logical_relation.relation_type IS '关系类型: 1:1, 1:N, N:1 (辅助LLM理解数据基数，可选)';
COMMENT ON COLUMN logical_relation.description IS '业务描述: 存入Prompt中帮助LLM理解 (例如: 订单表通过buyer_uid关联用户表id)';
COMMENT ON COLUMN logical_relation.is_deleted IS '逻辑删除: 0-未删除, 1-已删除';
COMMENT ON COLUMN logical_relation.created_time IS '创建时间';
COMMENT ON COLUMN logical_relation.updated_time IS '更新时间';
-- 逻辑外键配置表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_logical_relation_datasource_id ON logical_relation(datasource_id);
CREATE INDEX IF NOT EXISTS idx_logical_relation_source_table ON logical_relation(datasource_id, source_table_name);

-- 智能体数据源关联表
CREATE TABLE IF NOT EXISTS agent_datasource (
  id SERIAL NOT NULL,
  agent_id INT NOT NULL,
  datasource_id INT NOT NULL,
  is_active SMALLINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_datasource (agent_id, datasource_id),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE,
  FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
);
-- 智能体数据源关联表字段注释
COMMENT ON TABLE agent_datasource IS '智能体数据源关联表';
COMMENT ON COLUMN agent_datasource.id IS '';
COMMENT ON COLUMN agent_datasource.agent_id IS '智能体ID';
COMMENT ON COLUMN agent_datasource.datasource_id IS '数据源ID';
COMMENT ON COLUMN agent_datasource.is_active IS '是否启用：0-禁用，1-启用';
COMMENT ON COLUMN agent_datasource.create_time IS '创建时间';
COMMENT ON COLUMN agent_datasource.update_time IS '更新时间';
-- 智能体数据源关联表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_agent_datasource_agent_id ON agent_datasource(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_datasource_datasource_id ON agent_datasource(datasource_id);
CREATE INDEX IF NOT EXISTS idx_agent_datasource_is_active ON agent_datasource(is_active);

-- 智能体预设问题表
CREATE TABLE IF NOT EXISTS agent_preset_question (
  id SERIAL NOT NULL,
  agent_id INT NOT NULL,
  question TEXT NOT NULL,
  sort_order INT DEFAULT 0,
  is_active SMALLINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
-- 智能体预设问题表字段注释
COMMENT ON TABLE agent_preset_question IS '智能体预设问题表';
COMMENT ON COLUMN agent_preset_question.id IS '';
COMMENT ON COLUMN agent_preset_question.agent_id IS '智能体ID';
COMMENT ON COLUMN agent_preset_question.question IS '预设问题内容';
COMMENT ON COLUMN agent_preset_question.sort_order IS '排序顺序';
COMMENT ON COLUMN agent_preset_question.is_active IS '是否启用：0-禁用，1-启用';
COMMENT ON COLUMN agent_preset_question.create_time IS '创建时间';
COMMENT ON COLUMN agent_preset_question.update_time IS '更新时间';
-- 智能体预设问题表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_agent_preset_question_agent_id ON agent_preset_question(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_preset_question_sort_order ON agent_preset_question(sort_order);
CREATE INDEX IF NOT EXISTS idx_agent_preset_question_is_active ON agent_preset_question(is_active);

-- 会话表
CREATE TABLE IF NOT EXISTS chat_session (
  id VARCHAR(36) NOT NULL,
  agent_id INT NOT NULL,
  title VARCHAR(255) DEFAULT '新对话',
  status VARCHAR(50) DEFAULT 'active',
  is_pinned SMALLINT DEFAULT 0,
  user_id VARCHAR(33),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
-- 会话表字段注释
COMMENT ON TABLE chat_session IS '聊天会话表';
COMMENT ON COLUMN chat_session.id IS '会话ID（UUID）';
COMMENT ON COLUMN chat_session.agent_id IS '智能体ID';
COMMENT ON COLUMN chat_session.title IS '会话标题';
COMMENT ON COLUMN chat_session.status IS '状态：active-活跃，archived-归档，deleted-已删除';
COMMENT ON COLUMN chat_session.is_pinned IS '是否置顶：0-否，1-是';
COMMENT ON COLUMN chat_session.user_id IS '用户ID';
COMMENT ON COLUMN chat_session.create_time IS '创建时间';
COMMENT ON COLUMN chat_session.update_time IS '更新时间';
-- 会话表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_chat_session_agent_id ON chat_session(agent_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_user_id ON chat_session(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_status ON chat_session(status);
CREATE INDEX IF NOT EXISTS idx_chat_session_is_pinned ON chat_session(is_pinned);
CREATE INDEX IF NOT EXISTS idx_chat_session_create_time ON chat_session(create_time);

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGSERIAL NOT NULL,
  session_id VARCHAR(36) NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  message_type VARCHAR(50) DEFAULT 'text',
  metadata JSON,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);
-- 消息表字段注释
COMMENT ON TABLE chat_message IS '聊天消息表';
COMMENT ON COLUMN chat_message.id IS '';
COMMENT ON COLUMN chat_message.session_id IS '会话ID';
COMMENT ON COLUMN chat_message.role IS '角色：user-用户，assistant-助手，system-系统';
COMMENT ON COLUMN chat_message.content IS '消息内容';
COMMENT ON COLUMN chat_message.message_type IS '消息类型：text-文本，sql-SQL查询，result-查询结果，error-错误';
COMMENT ON COLUMN chat_message.metadata IS '元数据（JSON格式）';
COMMENT ON COLUMN chat_message.create_time IS '创建时间';
-- 消息表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_role ON chat_message(role);
CREATE INDEX IF NOT EXISTS idx_chat_message_message_type ON chat_message(message_type);
CREATE INDEX IF NOT EXISTS idx_chat_message_create_time ON chat_message(create_time);

-- 用户Prompt配置表
CREATE TABLE IF NOT EXISTS user_prompt_config (
  id VARCHAR(36) NOT NULL,
  name VARCHAR(255) NOT NULL,
  prompt_type VARCHAR(100) NOT NULL,
  agent_id INT,
  system_prompt TEXT NOT NULL,
  enabled SMALLINT DEFAULT 1,
  description TEXT,
  priority INT DEFAULT 0,
  display_order INT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  creator VARCHAR(255),
  PRIMARY KEY (id)
);
-- 用户Prompt配置表字段注释
COMMENT ON TABLE user_prompt_config IS '用户Prompt配置表';
COMMENT ON COLUMN user_prompt_config.id IS '配置ID（UUID）';
COMMENT ON COLUMN user_prompt_config.name IS '配置名称';
COMMENT ON COLUMN user_prompt_config.prompt_type IS 'Prompt类型（如report-generator, planner等）';
COMMENT ON COLUMN user_prompt_config.agent_id IS '关联的智能体ID，为空表示全局配置';
COMMENT ON COLUMN user_prompt_config.system_prompt IS '用户自定义系统Prompt内容';
COMMENT ON COLUMN user_prompt_config.enabled IS '是否启用该配置：0-禁用，1-启用';
COMMENT ON COLUMN user_prompt_config.description IS '配置描述';
COMMENT ON COLUMN user_prompt_config.priority IS '配置优先级，数字越大优先级越高';
COMMENT ON COLUMN user_prompt_config.display_order IS '配置显示顺序，数字越小越靠前';
COMMENT ON COLUMN user_prompt_config.create_time IS '创建时间';
COMMENT ON COLUMN user_prompt_config.update_time IS '更新时间';
COMMENT ON COLUMN user_prompt_config.creator IS '创建者';
-- 用户Prompt配置表索引（全局唯一命名）
CREATE INDEX IF NOT EXISTS idx_user_prompt_config_type ON user_prompt_config(prompt_type);
CREATE INDEX IF NOT EXISTS idx_user_prompt_config_agent_id ON user_prompt_config(agent_id);
CREATE INDEX IF NOT EXISTS idx_user_prompt_config_enabled ON user_prompt_config(enabled);
CREATE INDEX IF NOT EXISTS idx_user_prompt_config_create_time ON user_prompt_config(create_time);
CREATE INDEX IF NOT EXISTS idx_user_prompt_config_type_agent_enabled_priority ON user_prompt_config(prompt_type, agent_id, enabled, priority DESC);
CREATE INDEX IF NOT EXISTS idx_user_prompt_config_display_order ON user_prompt_config(display_order ASC);

-- 智能体数据源数据表关联表
CREATE TABLE IF NOT EXISTS agent_datasource_tables (
    id SERIAL NOT NULL PRIMARY KEY,
    agent_datasource_id INT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE CONSTRAINT uk_agent_datasource_tables_agent_ds_table_name UNIQUE (agent_datasource_id, table_name),
    CONSTRAINT fk_agent_datasource_tables_agent_ds_id FOREIGN KEY (agent_datasource_id) REFERENCES agent_datasource (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
-- 智能体数据源数据表关联表字段注释
COMMENT ON TABLE agent_datasource_tables IS '某个智能体某个数据源所选中的数据表';
COMMENT ON COLUMN agent_datasource_tables.id IS '';
COMMENT ON COLUMN agent_datasource_tables.agent_datasource_id IS '智能体数据源ID';
COMMENT ON COLUMN agent_datasource_tables.table_name IS '数据表名';
COMMENT ON COLUMN agent_datasource_tables.create_time IS '创建时间';
COMMENT ON COLUMN agent_datasource_tables.update_time IS '更新时间';

-- 模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id SERIAL NOT NULL,
    provider VARCHAR(255) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    temperature DECIMAL(10,2) DEFAULT '0.00',
    is_active boolean DEFAULT '0',
    max_tokens INT DEFAULT '2000',
    model_type VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    completions_path VARCHAR(255) DEFAULT NULL,
    embeddings_path VARCHAR(255) DEFAULT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT '0',
    proxy_enabled boolean DEFAULT '0',
    proxy_host VARCHAR(255) DEFAULT NULL,
    proxy_port INT DEFAULT NULL,
    proxy_username VARCHAR(255) DEFAULT NULL,
    proxy_password VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
);
-- 模型配置表字段注释
COMMENT ON TABLE model_config IS '';
COMMENT ON COLUMN model_config.id IS '';
COMMENT ON COLUMN model_config.provider IS '厂商标识 (方便前端展示回显，实际调用主要靠 baseUrl)';
COMMENT ON COLUMN model_config.base_url IS '关键配置';
COMMENT ON COLUMN model_config.api_key IS 'API密钥';
COMMENT ON COLUMN model_config.model_name IS '模型名称';
COMMENT ON COLUMN model_config.temperature IS '温度参数';
COMMENT ON COLUMN model_config.is_active IS '是否激活';
COMMENT ON COLUMN model_config.max_tokens IS '输出响应最大令牌数';
COMMENT ON COLUMN model_config.model_type IS '模型类型 (CHAT/EMBEDDING)';
COMMENT ON COLUMN model_config.completions_path IS 'Chat模型专用。附加到 Base URL 的路径。例如OpenAi的/v1/chat/completions';
COMMENT ON COLUMN model_config.embeddings_path IS '嵌入模型专用。附加到 Base URL 的路径。';
COMMENT ON COLUMN model_config.created_time IS '创建时间';
COMMENT ON COLUMN model_config.updated_time IS '更新时间';
COMMENT ON COLUMN model_config.is_deleted IS '0=未删除, 1=已删除';
COMMENT ON COLUMN model_config.proxy_enabled IS '是否启用代理：0-禁用，1-启用';
COMMENT ON COLUMN model_config.proxy_host IS '代理主机地址';
COMMENT ON COLUMN model_config.proxy_port IS '代理端口';
COMMENT ON COLUMN model_config.proxy_username IS '代理用户名（可选）';
COMMENT ON COLUMN model_config.proxy_password IS '代理密码（可选）';