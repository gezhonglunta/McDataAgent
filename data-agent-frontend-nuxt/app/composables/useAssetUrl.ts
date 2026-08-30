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
 * 资源 URL 归一化：后端返回的相对资源路径（如 /uploads/xxx）需补上部署基座前缀（apiBase），
 * 才能在网关按 /nl2sql/** 转发时被正确路由到后端。
 */

/**
 * 把后端返回的相对资源路径补上部署基座前缀。
 *
 * @param path 后端返回的资源路径（可为空、data/blob 地址、完整 http 地址或相对路径）
 * @returns 浏览器可直接访问的资源地址
 */
export function resolveAssetUrl(path?: string | null): string {
	if (!path) return '';

	const trimmed = String(path).trim();

	// data/blob 内联地址、完整 url、协议相对地址直接使用
	if (/^(data:|blob:|https?:\/\/|\/\/)/.test(trimmed)) {
		return trimmed;
	}

	const {
		public: { apiBase },
	} = useRuntimeConfig();

	if (apiBase && trimmed.startsWith('/') && !trimmed.startsWith(`${apiBase}/`)) {
		return `${apiBase}${trimmed}`;
	}

	return trimmed;
}

export function useAssetUrl() {
	return resolveAssetUrl;
}