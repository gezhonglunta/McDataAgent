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
 * @description 全局 Cookie 解析工具，用于从浏览器 cookie 中读取管理端登录 token。
 */

/**
 * 将 `document.cookie` 解析为 key-value 对象。
 */
export function parseCookies(): Record<string, string> {
	if (typeof document === 'undefined') return {};

	const result: Record<string, string> = {};
	for (const part of document.cookie.split(';')) {
		const idx = part.indexOf('=');
		if (idx < 0) continue;

		const key = part.slice(0, idx).trim();
		const raw = part.slice(idx + 1).trim();
		if (!key) continue;

		try {
			result[key] = decodeURIComponent(raw);
		} catch {
			result[key] = raw;
		}
	}
	return result;
}

/**
 * 根据 cookie 名读取值，不存在时返回 null。
 */
export function getCookie(name: string): string | null {
	const value = parseCookies()[name];
	return value ?? null;
}

/**
 * 解析管理端 token。token 存放于 cookie `Admin_{host}_{port}` 中，
 * 其中 host/port 为当前浏览器访问主机的 hostname 与端口号。
 *
 * 优先精确匹配 `Admin_{host}_{port}`；找不到时退回任意 `Admin_` 开头的
 * cookie（同一站点管理 token 唯一）。
 */
export function resolveAdminToken(): string | null {
	if (typeof window === 'undefined') return null;

	const { hostname, port } = window.location;
	const cookies = parseCookies();
	const preferred = `Admin_${hostname}_${port}`;
	if (cookies[preferred]) return cookies[preferred];

	const fallbackKey = Object.keys(cookies).find((key) => key.startsWith('Admin_'));
	return fallbackKey ? cookies[fallbackKey] : null;
}

/**
 * 返回带 `Authorization: Bearer {token}` 的请求头；
 * token 缺失时返回空对象，便于直接展开进请求 options。
 */
export function getAuthHeaders(): Record<string, string> {
	const token = resolveAdminToken();
	return token ? { Authorization: `Bearer ${token}` } : {};
}