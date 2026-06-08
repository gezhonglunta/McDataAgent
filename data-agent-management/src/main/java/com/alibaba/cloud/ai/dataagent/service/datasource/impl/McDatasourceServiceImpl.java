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

	private static final Map<String, Map<String, String>> COLUMN_INFO_MAP = new HashMap<>(8);

	static {
		Map<String, String> syDictMap = new HashMap<>(8);
		COLUMN_INFO_MAP.put("sy_dict", syDictMap);
		syDictMap.put("value", "字典名称，用于信息展示");
		syDictMap.put("code", "字典编码，用于展示");
		syDictMap.put("scope", "字典级别：0-系统级，1-平台级，2-业务级");
		syDictMap.put("remark", "字典备注，通常描述字典的使用场景");
		syDictMap.put("dict_id", "字典id，主键");

		Map<String, String> syDictValMap = new HashMap<>(8);
		COLUMN_INFO_MAP.put("sy_dict_val", syDictValMap);
		syDictValMap.put("value", "字典值名称，用于信息展示");
		syDictValMap.put("code", "字典值编码，用于代码引用");
		syDictValMap.put("dict_id", "字典id，关联 sy_dict.dict_id 的外键");
		syDictValMap.put("dict_val_id", "字典值id，主键");

		Map<String, String> syUserMap = new HashMap<>(8);
		COLUMN_INFO_MAP.put("sy_user", syUserMap);
		syUserMap.put("id", "用户id，主键");
		syUserMap.put("name", "用户姓名");
		syUserMap.put("login_name", "用户登录名");
		syUserMap.put("enable", "启用状态，1-启动，0-未启用");
		syUserMap.put("deleted", "逻辑删除状态，1-已删除，0-未删除");
		syUserMap.put("three_member_user", "是否三员账号,1-是,0-否");
		syUserMap.put("is_system", "是否系统用户,1-系统用户（三员3个账号、单员admin账号、数字大屏账号） 0-非系统用户");
		syUserMap.put("login_type", "是否开发者，0-操作员，1-开发者");

		Map<String, String> syDeptMap = new HashMap<>(8);
		COLUMN_INFO_MAP.put("sy_dept", syDeptMap);
		syDeptMap.put("id", "(组织机构/部门)id，主键");
		syDeptMap.put("up_id", "上级单位ID");
		syDeptMap.put("name", "单位名称");
		syDeptMap.put("is_auth", "是否是组织机构，1-是，0-否；为否是表示当前记录为部门");
		syDeptMap.put("deleted", "逻辑删除状态，1-已删除，0-未删除");
		syDeptMap.put("dep_sn", "部门编码");

		Map<String, String> syRoleMap = new HashMap<>(8);
		COLUMN_INFO_MAP.put("sy_role", syRoleMap);
		syRoleMap.put("id", "角色id，主键");
		syRoleMap.put("name", "角色名称");
		syRoleMap.put("remark", "描述，备注");
		syRoleMap.put("three_member_role", "是否三员角色,1-是,0-否");
		syRoleMap.put("is_system", "是否系统角色,1-系统角色（三员3个角色、单员1个管理员角色），0-非系统角色");
		syRoleMap.put("role_type", "角色类型,normal-通用角色，buss-业务角色");
		syRoleMap.put("data_range", "数据范围，all-全部组织机构，custom-自定组织机构");
		syRoleMap.put("use_case", "使用场景，share-多组织共享角色，cross-跨组织数据维护");

	}

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
			tableNames = tablesMapper.getAgentDatasourceTables(agentDatasourceId)
					.stream()
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
			throw new RuntimeException(
					"Failed to query MC metadata for datasource " + datasourceId + ": " + e.getMessage(), e);
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
			if (StringUtils.isBlank(table.getDescription())
					&& (mcDesc = mcTableList.get(table.getName().toUpperCase())) != null) {
				table.setDescription(mcDesc);
			}
			if (StringUtils.isBlank(table.getDescription())) {
				log.error("缺失表：{} 的描述信息", table.getName());
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
			List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null
					? resultSetBO.getData() : Collections.emptyList();
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
			List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null
					? resultSetBO.getData() : Collections.emptyList();
			Set<String> mcTableNames = rows.stream()
					.map(e -> e.get("table_name"))
					.map(e -> e.toLowerCase())
					.collect(Collectors.toSet());
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
				(value:TEXT, 字典名称，用于信息展示 , Examples: [状态,分类,单位]),
				(code:TEXT, 字典编码，用于代码引用 , Examples: [STATUS,TYPE,UNIT]),
				(scope:TEXT, 字典级别：0-系统级，1-平台级，2-业务级 , Examples: [0,1]),
				(remark:TEXT, 字典备注，通常描述字典的使用场景 , Examples: [在采购单据中使用]),
				(dict_id:TEXT, 字典id , Primary Key, Examples: [7ffd01666a18e17b62a431b82a8e9282,a762b97ef911418fbe989876718339d7])
				]
				""";
		String dictVal = """
				# Table: sy_dict_val, 系统数据字典值表
				[
				(value:TEXT, 字典值名称，用于信息展示 , Examples: [烤箱,否,082009]),
				(code:TEXT, 字典值编码，用于代码引用 , Examples: [KX,2,082009]),
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

	@Override
	public String filterColumnDescription(String description, String columnName, String tableName) {
		if (StringUtils.isNotBlank(description)) {
			return description;
		}
		Map<String, String> columnInfo = COLUMN_INFO_MAP.get(tableName.toLowerCase());
		String newDesc = columnInfo == null ? null : columnInfo.get(columnName.toLowerCase());
		if (newDesc == null) {
			log.error("缺失表：{} 的字段：{} 配置信息", tableName, columnName);
			return "";
		}
		return newDesc;
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
			Set<String> tableNames = tables.stream()
					.map(e -> e.getName())
					.map(String::toUpperCase)
					.collect(Collectors.toSet());
			StringJoiner tableInClause = new StringJoiner(", ");
			for (String tableName : tableNames) {
				tableInClause.add("'" + tableName + "'");
			}
			String sql = "select table_name,orm_name from ms_orm_model where table_name in (%s)"
					.formatted(tableInClause.toString());
			DbQueryParameter queryParameter = DbQueryParameter.from(config);
			queryParameter.setSql(sql);
			queryParameter.setSchema(config.getSchema());
			ResultSetBO resultSetBO = dbAccessor.executeSqlAndReturnObject(config, queryParameter);
			List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null
					? resultSetBO.getData() : Collections.emptyList();
			Map<String, String> result = rows.stream().collect(Collectors.toMap(e -> e.get("table_name"), e -> e.get("orm_name")));
			result.put("sy_role", "系统角色表");
			result.put("sy_user_role", "系统用户和角色关系表");
			return result;
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
