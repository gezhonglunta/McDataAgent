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

import axios from 'axios';
import { resolveAdminToken } from '~/utils/cookie';
import { redirectToAdminLogin } from '~/utils/auth';

export default defineNuxtPlugin(() => {
	const {
		public: { apiBase },
	} = useRuntimeConfig();
	axios.defaults.baseURL = apiBase;

	axios.interceptors.request.use((config) => {
		const token = resolveAdminToken();
		if (token) {
			config.headers.set('Authorization', `Bearer ${token}`);
		}
		return config;
	});

	axios.interceptors.response.use(
		(response) => {
			if (response.data?.code === '401') {
				redirectToAdminLogin();
			}
			return response;
		},
		(error) => {
			if (error.response?.data?.code === '401') {
				redirectToAdminLogin();
			}
			return Promise.reject(error);
		},
	);
});
