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
package com.alibaba.cloud.ai.dataagent.service.mcdata.model;

/**
 * 业务表使用的数据字典项
 *
 * @author 张华
 */
public class McDictItem {

	/**
	 * 数据字典项编码，通常是表记录的存储值
	 */
	private String dictItemCode;

	/**
	 * 数据字典项名称，通常是表记录的展示值
	 */
	private String dictItemName;

	public String getDictItemCode() {
		return dictItemCode;
	}

	public void setDictItemCode(String dictItemCode) {
		this.dictItemCode = dictItemCode;
	}

	public String getDictItemName() {
		return dictItemName;
	}

	public void setDictItemName(String dictItemName) {
		this.dictItemName = dictItemName;
	}

}
