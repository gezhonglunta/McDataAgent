/*
 * Copyright 2024-2025 the original author or authors.
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
import { ElMessageBox } from 'element-plus';

const API_BASE = import.meta.env.VITE_API_BASE || '';
const AUTH_DEBUG_PREFIX = '[auth-debug]';
const UNAUTHORIZED_CODES = new Set(['401', '403']);
let isShowingUnauthorizedDialog = false;

export const apiUrl = (path: string): string => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE}${normalizedPath}`;
};

const parseCookies = (): Record<string, string> => {
  return document.cookie
    .split(';')
    .map(cookie => cookie.trim())
    .filter(Boolean)
    .reduce<Record<string, string>>((cookies, cookie) => {
      const separatorIndex = cookie.indexOf('=');
      if (separatorIndex < 0) {
        return cookies;
      }
      const name = cookie.slice(0, separatorIndex).trim();
      const value = cookie.slice(separatorIndex + 1).trim();
      cookies[name] = decodeURIComponent(value);
      return cookies;
    }, {});
};

const currentGatewayPort = (): string => {
  if (window.location.port) {
    return window.location.port;
  }
  return window.location.protocol === 'https:' ? '443' : '80';
};

const bearerToken = (): string | null => {
  const cookies = parseCookies();
  const port = currentGatewayPort();
  const adminCookieName = `Admin_${window.location.hostname}_${port}`;
  const bearerCookieName = `Bearer_${window.location.hostname}_${port}`;

  console.log(`${AUTH_DEBUG_PREFIX} resolving bearer token`, {
    location: window.location.href,
    hostname: window.location.hostname,
    port,
    expectedAdminCookieName: adminCookieName,
    expectedBearerCookieName: bearerCookieName,
    rawCookie: document.cookie,
    cookieNames: Object.keys(cookies),
  });

  if (cookies[adminCookieName]) {
    console.log(`${AUTH_DEBUG_PREFIX} matched expected admin cookie`, {
      cookieName: adminCookieName,
      tokenPreview: `${cookies[adminCookieName].slice(0, 12)}...`,
    });
    return cookies[adminCookieName];
  }

  if (cookies[bearerCookieName]) {
    console.log(`${AUTH_DEBUG_PREFIX} admin cookie missing, matched expected bearer cookie`, {
      adminCookieName,
      cookieName: bearerCookieName,
      tokenPreview: `${cookies[bearerCookieName].slice(0, 12)}...`,
    });
    return cookies[bearerCookieName];
  }

  const matchedEntry = Object.entries(cookies).find(([name]) => name.startsWith('Admin_'));
  if (matchedEntry) {
    console.warn(`${AUTH_DEBUG_PREFIX} expected admin cookie missing, fallback Admin_* matched`, {
      expectedAdminCookieName: adminCookieName,
      matchedCookieName: matchedEntry[0],
      tokenPreview: `${matchedEntry[1].slice(0, 12)}...`,
    });
    return matchedEntry[1];
  }

  const matchedBearerEntry = Object.entries(cookies).find(([name]) => name.startsWith('Bearer_'));
  if (matchedBearerEntry) {
    console.warn(`${AUTH_DEBUG_PREFIX} expected cookie missing, fallback Bearer_* matched`, {
      expectedBearerCookieName: bearerCookieName,
      matchedCookieName: matchedBearerEntry[0],
      tokenPreview: `${matchedBearerEntry[1].slice(0, 12)}...`,
    });
    return matchedBearerEntry[1];
  }

  if (cookies.Admin) {
    console.warn(`${AUTH_DEBUG_PREFIX} expected cookies missing, fallback Admin matched`, {
      expectedAdminCookieName: adminCookieName,
      tokenPreview: `${cookies.Admin.slice(0, 12)}...`,
    });
    return cookies.Admin;
  }

  if (cookies.Bearer) {
    console.warn(`${AUTH_DEBUG_PREFIX} expected cookie missing, fallback Bearer matched`, {
      expectedBearerCookieName: bearerCookieName,
      tokenPreview: `${cookies.Bearer.slice(0, 12)}...`,
    });
    return cookies.Bearer;
  }

  if (!cookies.Admin && !cookies.Bearer) {
    console.error(`${AUTH_DEBUG_PREFIX} no bearer token cookie found`, {
      expectedAdminCookieName: adminCookieName,
      expectedBearerCookieName: bearerCookieName,
      rawCookie: document.cookie,
      cookieNames: Object.keys(cookies),
    });
  }

  return null;
};

export const authHeaders = (): Record<string, string> => {
  const token = bearerToken();
  console.log(`${AUTH_DEBUG_PREFIX} building auth headers`, {
    hasToken: Boolean(token),
    headerApplied: Boolean(token),
  });
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export const apiFetch = (path: string, init: RequestInit = {}): Promise<Response> => {
  const headers = {
    ...authHeaders(),
    ...init.headers,
  };

  console.log(`${AUTH_DEBUG_PREFIX} fetch request`, {
    path,
    url: apiUrl(path),
    method: init.method || 'GET',
    headers,
  });

  return fetch(apiUrl(path), {
    ...init,
    headers,
  });
};

axios.interceptors.request.use(config => {
  const token = bearerToken();
  console.log(`${AUTH_DEBUG_PREFIX} axios request interceptor`, {
    url: config.url,
    method: config.method,
    hasToken: Boolean(token),
    originalHeaders: config.headers,
  });
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
    console.log(`${AUTH_DEBUG_PREFIX} axios authorization applied`, {
      url: config.url,
      authorizationPreview: `Bearer ${token.slice(0, 12)}...`,
    });
  } else {
    console.error(`${AUTH_DEBUG_PREFIX} axios request missing token`, {
      url: config.url,
      method: config.method,
    });
  }
  return config;
});

export interface ApiResponse<T = unknown> {
  success: boolean;
  message: string;
  data?: T;
  code?: string | number;
  msg?: string;
}

export const isUnauthorizedCode = (code: string | number | undefined | null): boolean => {
  return code != null && UNAUTHORIZED_CODES.has(String(code));
};

export const isUnauthorizedResponseData = (responseData: { code?: string | number } | undefined): boolean => {
  return isUnauthorizedCode(responseData?.code);
};

export const createUnauthorizedError = (responseData?: { code?: string | number } | undefined): Error => {
  const status = isUnauthorizedCode(responseData?.code) ? Number(responseData?.code) : 401;
  const error = new Error('未授权');
  Object.assign(error, {
    response: {
      status,
      data: responseData,
    },
  });
  return error;
};

export const isUnauthorizedError = (error: any): boolean => {
  return isUnauthorizedCode(error?.response?.status) || isUnauthorizedResponseData(error?.response?.data);
};

export const redirectToLoginWithPrompt = (): void => {
  if (isShowingUnauthorizedDialog) {
    return;
  }

  isShowingUnauthorizedDialog = true;
  ElMessageBox.alert('未登录，请先登录。', '提示', {
    confirmButtonText: '确定',
    type: 'warning',
    closeOnClickModal: false,
    closeOnPressEscape: false,
    showClose: false,
  })
    .then(() => {
      window.location.href = '/admin/sso/admin/login';
    })
    .finally(() => {
      isShowingUnauthorizedDialog = false;
    });
};

export interface PageResponse<T = unknown> {
  success: boolean;
  message: string;
  data: T;
  total: number;
  pageNum: number;
  pageSize: number;
  totalPages: number;
}
