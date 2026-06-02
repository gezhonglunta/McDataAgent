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
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.mapper.LogicalRelationMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.McDatasourceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class McDatasourceServiceImpl implements McDatasourceService {

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final LogicalRelationMapper logicalRelationMapper;

	private final AgentDatasourceTablesMapper tablesMapper;

	private final DatasourceService datasourceService;

	private final AccessorFactory accessorFactory;

	@Override
	public DbConfigBO getDbConfigByDatasourceId(Integer datasourceId) {
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}
		return datasourceService.getDbConfig(datasource);
	}

	@Override
	public Accessor getAccessorByDatasourceId(Integer datasourceId) {
		DbConfigBO dbConfig = getDbConfigByDatasourceId(datasourceId);
		return accessorFactory.getAccessorByDbConfig(dbConfig);
	}

	@Override
	public void saveMcLogicalRelations(Long agentId, Integer datasourceId) {
		List<String> tableNames;
		try {
			int agentDatasourceId = agentDatasourceMapper.getIdByAgentIdAndDatasourceId(agentId, datasourceId);
			tableNames = tablesMapper.getAgentDatasourceTables(agentDatasourceId).stream()
					.filter(Objects::nonNull)
					.map(table -> table.trim().toUpperCase(Locale.ROOT))
					.filter(table -> !table.isEmpty())
					.toList();
		} catch (Exception e) {
			log.error("找不到智能体的数据源配置：agentId={},datasourceId={}", agentId, datasourceId, e);
			return;
		}

		if (tableNames.isEmpty()) {
			return;
		}

		DbConfigBO dbConfig = getDbConfigByDatasourceId(datasourceId);
		Accessor accessor = getAccessorByDatasourceId(datasourceId);

		StringJoiner tableInClause = new StringJoiner(", ");
		for (String tableName : tableNames) {
			tableInClause.add("'" + tableName + "'");
		}

		String sql = """
				select src_orm.table_name as src_table_name,
				src_orm.orm_name as src_orm_name,
				src_orm_col.col_no as src_table_col_no,
				dst_orm.table_name as dst_table_name,
				dst_orm.orm_name as dst_orm_name,
				dst_orm_col.col_no as dst_table_col_no
				from ms_orm_model_rel rel inner join
				ms_orm_model src_orm on rel.orm_id = src_orm.id
				inner join ms_orm_model_col src_orm_col on rel.orm_col_id = src_orm_col.id
				inner join ms_orm_model dst_orm on rel.refer_orm_id = dst_orm.id
				inner join ms_orm_model_col dst_orm_col on rel.refer_orm_col_id = dst_orm_col.id
				where rel.join_type = 'OneToOne'
				and src_orm.table_name in (%s)
				""".formatted(tableInClause.toString());

		DbQueryParameter queryParameter = DbQueryParameter.from(dbConfig);
		queryParameter.setSql(sql);
		queryParameter.setSchema(dbConfig.getSchema());

		ResultSetBO resultSetBO;
		try {
			resultSetBO = accessor.executeSqlAndReturnObject(dbConfig, queryParameter);
		} catch (Exception e) {
			throw new RuntimeException("Failed to query MC metadata for datasource " + datasourceId + ": "
					+ e.getMessage(), e);
		}

		List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null ? resultSetBO.getData()
				: Collections.emptyList();
		if (rows.isEmpty()) {
			log.info("No MC logical relations found for datasource {}", datasourceId);
			datasourceService.saveLogicalRelations(datasourceId, Collections.emptyList());
			return;
		}

		List<LogicalRelation> logicalRelations = rows.stream()
				.map(row -> LogicalRelation.builder()
						.datasourceId(datasourceId)
						.sourceTableName(lower(row.get("src_table_name")))
						.sourceColumnName(lower(row.get("src_table_col_no")))
						.targetTableName(lower(row.get("dst_table_name")))
						.targetColumnName(lower(row.get("dst_table_col_no")))
						.relationType("1:1")
						.description(String.format("%s通过%s关联%s%s", ensureTableSuffix(row.get("src_orm_name")),
								lower(row.get("src_table_col_no")), ensureTableSuffix(row.get("dst_orm_name")),
								lower(row.get("dst_table_col_no"))))
						.build())
				.toList();
		log.info("开始删除全部的关联关系，AgentId={},DataSourceId={}", agentId, datasourceId);
		logicalRelationMapper.deleteAll(datasourceId);
		datasourceService.saveLogicalRelations(datasourceId, logicalRelations);
	}

	@Override
	public void filterMcInfo(DbConfigBO config, Accessor dbAccessor, List<TableInfoBO> tables) {
		Map<String, String> mcTableList = tableDescList(config, dbAccessor, tables);

		for (TableInfoBO table : tables) {
			if (StringUtils.isBlank(table.getSchema())) {
				table.setSchema(config.getSchema());
			}
			String mcDesc;
			if (StringUtils.isBlank(table.getDescription()) && (mcDesc = mcTableList.get(table.getName().toUpperCase())) != null) {
				table.setDescription(mcDesc);
			}
		}
	}

	@Override
	public String listDictNamesForLLM(Integer datasourceId) {
		try {
			DbConfigBO dbConfig = getDbConfigByDatasourceId(datasourceId);
			Accessor accessor = getAccessorByDatasourceId(datasourceId);
			String sql = "select distinct value,code,remark from sy_dict where scope != '0'";
			DbQueryParameter queryParameter = DbQueryParameter.from(dbConfig);
			queryParameter.setSql(sql);
			queryParameter.setSchema(dbConfig.getSchema());
			ResultSetBO resultSetBO = accessor.executeSqlAndReturnObject(dbConfig, queryParameter);
			List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null ? resultSetBO.getData()
					: Collections.emptyList();
			return formatDictNamesAsMarkdownTable(rows);
		} catch (Exception e) {
			log.error("查找MC业务表说明失败：datasourceId={}", datasourceId, e);
		}
		return "";
	}

	@Override
	public Set<String> mcBussTableNames(DbConfigBO dbConfig, Accessor dbAccessor) {
		try {
			String sql = "select table_name from ms_orm_model";
			DbQueryParameter queryParameter = DbQueryParameter.from(dbConfig);
			queryParameter.setSql(sql);
			queryParameter.setSchema(dbConfig.getSchema());
			ResultSetBO resultSetBO = dbAccessor.executeSqlAndReturnObject(dbConfig, queryParameter);
			List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null ? resultSetBO.getData()
					: Collections.emptyList();
			Set<String> mcTableNames = rows.stream().map(e -> e.get("table_name")).map(e -> e.toLowerCase()).collect(Collectors.toSet());
			mcTableNames.add("sy_dept");
			mcTableNames.add("sy_user");
			mcTableNames.add("sy_role");
			mcTableNames.add("sy_user_role");
			return mcTableNames;
		} catch (Exception e) {
			log.error("查找MC业务表说明失败：username={}", dbConfig.getUsername(), e);
			return new HashSet<>(0);
		}
	}

	@Override
	public String extBuildSemanticConsistenPrompt(String schemaInfo) {
		String dict = """
				# Table: sy_dict, 系统数据字典表
				[
				(value:TEXT, 字典名称，用于展示 , Examples: [默认查询数据,是否显示标题,控制数据权限]),
				(code:TEXT, 字典编码 , Examples: [IS_DEF_QUERY,IS_TITLE_ATTR,IS_DATA_AUTH]),
				(scope:TEXT, 字典级别：0-系统级，1-平台级，2-业务级 , Examples: [0,1]),
				(remark:TEXT, 字典备注 , Examples: [默认是1 否2]),
				(dict_id:TEXT, 字典id , Primary Key, Examples: [7ffd01666a18e17b62a431b82a8e9282,a762b97ef911418fbe989876718339d7])
				]
				""";
		String dictVal = """
				# Table: sy_dict_val, 系统数据字典值表
				[
				(value:TEXT, 字典值名称，用于展示 , Examples: [烤箱,否,082009]),
				(code:TEXT, 字典值编码 , Examples: [KX,2,082009]),
				(dict_id:TEXT, 字典id , Examples: [64ecb845083a47929a5920294fdbb305,87bb4b1c8a3141f584f7757eb58eaf03]),
				(dict_val_id:TEXT, 字典值id , Primary Key, Examples: [fe6e635a4afe4725b30eac8a73bdd09a,18c23300cd344f1da99b7912c7626880])
				]
				""";
		StringBuilder sb = new StringBuilder(schemaInfo);
		if (!StringUtils.contains(schemaInfo, "# Table: sy_dict")) {
			sb.append("\n").append(dict);
		}
		if (!StringUtils.contains(schemaInfo, "# Table: sy_dict_val")) {
			sb.append("\n").append(dictVal);
		}
		return sb.toString();
	}

	@Override
	public void extBuildSemanticModelPrompt(List<SemanticModel> semanticModels, Map<String, Object> params) {
		Integer datasourceId = CollectionUtils.isEmpty(semanticModels) ? null : semanticModels.get(0).getDatasourceId();
		if (datasourceId != null) {
			params.put("dict_list", listDictNamesForLLM(datasourceId));
		} else {
			params.put("dict_list", "");
		}
	}

	private String formatDictNamesAsMarkdownTable(List<Map<String, String>> rows) {
		if (rows.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("| 名称 | 编码 | 备注 |\n");
		sb.append("| ---- | ---- | ---- |\n");
		for (Map<String, String> row : rows) {
			String value = row.get("value") != null ? row.get("value") : "";
			String code = row.get("code") != null ? row.get("code") : "";
			String remark = row.get("remark") != null ? row.get("remark") : "";
			sb.append("| ").append(value).append(" | ").append(code).append(" | ").append(remark).append(" |\n");
		}
		return sb.toString();
	}

	private Map<String, String> tableDescList(DbConfigBO config, Accessor dbAccessor, List<TableInfoBO> tables) {
		try {
			Set<String> tableNames = tables.stream().map(e -> e.getName()).map(String::toUpperCase).collect(Collectors.toSet());
			StringJoiner tableInClause = new StringJoiner(", ");
			for (String tableName : tableNames) {
				tableInClause.add("'" + tableName + "'");
			}
			String sql = "select table_name,orm_name from ms_orm_model where table_name in (%s)".formatted(tableInClause.toString());
			DbQueryParameter queryParameter = DbQueryParameter.from(config);
			queryParameter.setSql(sql);
			queryParameter.setSchema(config.getSchema());
			ResultSetBO resultSetBO = dbAccessor.executeSqlAndReturnObject(config, queryParameter);
			List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null ? resultSetBO.getData()
					: Collections.emptyList();
			return rows.stream().collect(Collectors.toMap(e -> e.get("table_name"), e -> e.get("orm_name")));
		} catch (Exception e) {
			log.error("查找MC业务表说明失败：DbConfig={}", config, e);
			return new HashMap<>(0);
		}
	}

	private String ensureTableSuffix(String ormName) {
		if (ormName == null || ormName.isBlank()) {
			return "";
		}
		return ormName.endsWith("表") ? ormName : ormName + "表";
	}

	private String lower(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		return text.toLowerCase(Locale.ROOT);
	}
}
