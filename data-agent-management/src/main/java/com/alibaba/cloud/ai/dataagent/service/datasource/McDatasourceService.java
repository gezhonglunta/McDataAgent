/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.datasource;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface McDatasourceService {

	DbConfigBO getDbConfigByDatasourceId(Integer datasourceId);

	Accessor getAccessorByDatasourceId(Integer datasourceId);

	void saveMcLogicalRelations(Long agentId, Integer datasourceId);

	void filterMcInfo(DbConfigBO config, Accessor dbAccessor, List<TableInfoBO> tables);

	String listDictNamesForLLM(Integer datasourceId);

	Set<String> mcBussTableNames(DbConfigBO dbConfig, Accessor dbAccessor);

	String extBuildSemanticConsistenPrompt(String schemaInfo);

	void extBuildSemanticModelPrompt(List<SemanticModel> semanticModels, Map<String, Object> params);

	String filterColumnDescription(String description, String columnName, String tableName);
}
