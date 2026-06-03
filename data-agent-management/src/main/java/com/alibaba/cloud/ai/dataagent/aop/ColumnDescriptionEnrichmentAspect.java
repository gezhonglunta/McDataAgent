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
package com.alibaba.cloud.ai.dataagent.aop;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.service.datasource.McDatasourceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
@Slf4j
public class ColumnDescriptionEnrichmentAspect {

	@Autowired
	private McDatasourceService mcDatasourceService;

	@Pointcut("execution(* com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor.showColumns(..))")
	public void showColumnsPointcut() {
	}

	@AfterReturning(pointcut = "showColumnsPointcut()", returning = "columns")
	public void enrichColumnDescription(JoinPoint joinPoint, List<ColumnInfoBO> columns) {
		if (CollectionUtils.isEmpty(columns)) {
			return;
		}

		Object[] args = joinPoint.getArgs();
		DbQueryParameter param = null;
		for (Object arg : args) {
			if (arg instanceof DbQueryParameter) {
				param = (DbQueryParameter) arg;
				break;
			}
		}

		String tableName = param != null ? param.getTable() : "unknown";
		log.debug("Enriching column descriptions for table: {}, column count: {}", tableName, columns.size());

		for (ColumnInfoBO column : columns) {
			String original = column.getDescription();
			column.setDescription(mcDatasourceService.filterColumnDescription(original, column.getName(), tableName));
		}
	}

}
