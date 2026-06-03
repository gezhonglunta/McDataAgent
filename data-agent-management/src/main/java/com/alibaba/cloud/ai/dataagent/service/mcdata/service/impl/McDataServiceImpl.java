/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.mcdata.service.impl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.McDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.mcdata.model.McDict;
import com.alibaba.cloud.ai.dataagent.service.mcdata.model.McDictItem;
import com.alibaba.cloud.ai.dataagent.service.mcdata.service.McDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 张华
 */
@Slf4j
@Service
@AllArgsConstructor
public class McDataServiceImpl implements McDataService {

	private final McDatasourceService mcDatasourceService;

	private final DatasourceService datasourceService;

	@Override
	public List<McDict> listDict(Integer datasourceId, String dictName) {
		if (datasourceId == -1) {
			List<Datasource> allDatasource = datasourceService.getAllDatasource();
			datasourceId = allDatasource.get(0).getId();
		}
		log.info("开始查询MC数据字典：{}#{}", datasourceId, dictName);
		try {
			DbConfigBO dbConfig = mcDatasourceService.getDbConfigByDatasourceId(datasourceId);
			Accessor accessor = mcDatasourceService.getAccessorByDatasourceId(datasourceId);
			DbQueryParameter queryParameter = DbQueryParameter.from(dbConfig);
			String sql = """
					select d.code as dict_code,d.value as dict_name,dv.code as dict_item_code,dv.value as dict_item_name from sy_dict  d
					inner join sy_dict_val dv on d.dict_id = dv.dict_id
					where d.value like '%%%s%%' or  d.code like '%%%s%%'
					""";
			queryParameter.setSql(String.format(sql, dictName, dictName));
			queryParameter.setSchema(dbConfig.getSchema());
			ResultSetBO resultSetBO;
			try {
				resultSetBO = accessor.executeSqlAndReturnObject(dbConfig, queryParameter);
			}
			catch (Exception e) {
				throw new RuntimeException(
						"Failed to query MC metadata for datasource " + datasourceId + ": " + e.getMessage(), e);
			}
			List<Map<String, String>> rows = resultSetBO != null && resultSetBO.getData() != null
					? resultSetBO.getData() : Collections.emptyList();

			Map<String, McDict> dictMap = new LinkedHashMap<>();
			for (Map<String, String> row : rows) {
				String dictCode = row.get("dict_code");
				String currentDictName = row.get("dict_name");
				String mapKey = dictCode + "_" + currentDictName;

				McDict dict = dictMap.computeIfAbsent(mapKey, key -> {
					McDict item = new McDict();
					item.setDictCode(dictCode);
					item.setDictName(currentDictName);
					item.setItems(new java.util.ArrayList<>());
					return item;
				});

				McDictItem dictItem = new McDictItem();
				dictItem.setDictItemCode(row.get("dict_item_code"));
				dictItem.setDictItemName(row.get("dict_item_name"));
				dict.getItems().add(dictItem);
			}
			log.info("查询MC数据字典：{}#{},共{}条", datasourceId, dictName, dictMap.size());
			return List.copyOf(dictMap.values());
		}
		catch (Exception e) {
			log.error("查询MC数据字典失败：{}#{}", datasourceId, dictName, e);
			return new ArrayList<>(0);
		}
	}

}
