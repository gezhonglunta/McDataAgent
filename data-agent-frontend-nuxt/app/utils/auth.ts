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
 * @description 认证通用工具，统一处理后端业务错误码（如 code === '401'）时的登录跳转。
 */

/**
 * 未登录 / 会话超时的业务错误码。
 */
export const UNAUTHORIZED_CODE = '401';

/**
 * 引导浏览器跳转到管理端登录页；已在登录页时跳过，避免重复跳转。
 */
export function redirectToAdminLogin() {
	if (typeof window === 'undefined') return;
	if (window.location.pathname === '/admin') return;
	window.location.href = '/admin';
}