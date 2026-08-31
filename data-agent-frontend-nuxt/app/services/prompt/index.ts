/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @description 提示词配置管理服务，处理优化提示词的查询、保存、启用/禁用及优先级调整
 */

import axios from 'axios';

/**
 * @description 提示词配置实体接口
 */
export interface PromptConfig {
	/** 配置 ID */
	id?: number;
	/** 配置名称 */
	name: string;
	/** 配置描述 */
	description?: string;
	/** 优化提示词内容 */
	optimizationPrompt: string;
	/** 优先级 */
	priority?: number;
	/** 显示顺序 */
	displayOrder?: number;
	/** 是否启用 */
	enabled?: boolean;
	/** 提示词类型 */
	promptType: string;
	/** 关联的智能体 ID */
	agentId?: number | null;
	/** 创建者 */
	creator?: string;
}

/**
 * @description 提示词配置响应结构
 */
export interface PromptConfigResponse {
	/** 是否成功 */
	success: boolean;
	/** 提示消息 */
	message?: string;
	/** 返回数据 */
	data?: PromptConfig[] | PromptConfig;
}

const API_BASE_URL = '/api/prompt-config';

/**
 * @description 提示词配置业务逻辑处理类
 */
class PromptService {
	/**
	 * @description 根据类型加载优化配置列表
	 * @param {string} promptType - 提示词类型
	 * @param {string | number} [agentId] - 智能体 ID
	 * @returns {Promise<PromptConfig[]>} 配置列表
	 */
	async listByType(promptType: string, agentId?: string | number): Promise<PromptConfig[]> {
		const query = agentId ? `?agentId=${agentId}` : '';
		const response = await axios.get<PromptConfigResponse>(
			`${API_BASE_URL}/list-by-type/${promptType}${query}`,
		);
		return (response.data.data as PromptConfig[]) || [];
	}

	/**
	 * @description 保存提示词配置
	 * @param {PromptConfig} config - 配置信息
	 * @returns {Promise<PromptConfigResponse>} 操作结果
	 */
	async save(config: PromptConfig): Promise<PromptConfigResponse> {
		const response = await axios.post<PromptConfigResponse>(`${API_BASE_URL}/save`, config);
		return response.data;
	}

	/**
	 * @description 启用指定配置
	 * @param {number} id - 配置 ID
	 * @returns {Promise<PromptConfigResponse>} 操作结果
	 */
	async enable(id: number): Promise<PromptConfigResponse> {
		const response = await axios.post<PromptConfigResponse>(`${API_BASE_URL}/${id}/enable`);
		return response.data;
	}

	/**
	 * @description 禁用指定配置
	 * @param {number} id - 配置 ID
	 * @returns {Promise<PromptConfigResponse>} 操作结果
	 */
	async disable(id: number): Promise<PromptConfigResponse> {
		const response = await axios.post<PromptConfigResponse>(`${API_BASE_URL}/${id}/disable`);
		return response.data;
	}

	/**
	 * @description 删除指定配置
	 * @param {number} id - 配置 ID
	 * @returns {Promise<PromptConfigResponse>} 操作结果
	 */
	async delete(id: number): Promise<PromptConfigResponse> {
		const response = await axios.delete<PromptConfigResponse>(`${API_BASE_URL}/${id}`);
		return response.data;
	}

	/**
	 * @description 批量启用配置
	 * @param {number[]} ids - 配置 ID 列表
	 * @returns {Promise<PromptConfigResponse>} 操作结果
	 */
	async batchEnable(ids: number[]): Promise<PromptConfigResponse> {
		const response = await axios.post<PromptConfigResponse>(`${API_BASE_URL}/batch-enable`, ids);
		return response.data;
	}

	/**
	 * @description 批量禁用配置
	 * @param {number[]} ids - 配置 ID 列表
	 * @returns {Promise<PromptConfigResponse>} 操作结果
	 */
	async batchDisable(ids: number[]): Promise<PromptConfigResponse> {
		const response = await axios.post<PromptConfigResponse>(`${API_BASE_URL}/batch-disable`, ids);
		return response.data;
	}

	/**
	 * @description 更新配置的优先级
	 * @param {number} id - 配置 ID
	 * @param {number} priority - 优先级数值
	 * @returns {Promise<PromptConfigResponse>} 操作结果
	 */
	async updatePriority(id: number, priority: number): Promise<PromptConfigResponse> {
		const response = await axios.post<PromptConfigResponse>(`${API_BASE_URL}/${id}/priority`, {
			priority,
		});
		return response.data;
	}
}

export const promptService = new PromptService();