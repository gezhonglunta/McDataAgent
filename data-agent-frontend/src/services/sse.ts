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

import { apiUrl, authHeaders } from './common';

export interface SseEvent {
  event: string;
  data: string;
  id?: string;
}

export interface SseConnection {
  close: () => void;
}

interface ConnectSseOptions {
  headers?: Record<string, string>;
  onEvent?: (event: SseEvent) => Promise<void> | void;
  onError?: (error: Error) => Promise<void> | void;
  onClose?: () => Promise<void> | void;
}

interface PendingEvent {
  event: string;
  data: string[];
  id?: string;
}

const createPendingEvent = (): PendingEvent => ({
  event: 'message',
  data: [],
});

const toError = (error: unknown): Error => {
  if (error instanceof Error) {
    return error;
  }
  return new Error(typeof error === 'string' ? error : 'Unknown SSE error');
};

const dispatchEvent = async (
  pendingEvent: PendingEvent,
  onEvent?: (event: SseEvent) => Promise<void> | void,
): Promise<void> => {
  if (!onEvent || pendingEvent.data.length === 0) {
    return;
  }
  await onEvent({
    event: pendingEvent.event || 'message',
    data: pendingEvent.data.join('\n'),
    id: pendingEvent.id,
  });
};

const processEventBlock = async (
  block: string,
  onEvent?: (event: SseEvent) => Promise<void> | void,
): Promise<void> => {
  const pendingEvent = createPendingEvent();
  const lines = block.replace(/\r/g, '').split('\n');

  for (const line of lines) {
    if (!line || line.startsWith(':')) {
      continue;
    }

    const separatorIndex = line.indexOf(':');
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line;
    const rawValue = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : '';
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;

    switch (field) {
      case 'event':
        pendingEvent.event = value || 'message';
        break;
      case 'data':
        pendingEvent.data.push(value);
        break;
      case 'id':
        pendingEvent.id = value;
        break;
      default:
        break;
    }
  }

  await dispatchEvent(pendingEvent, onEvent);
};

const consumeStream = async (
  stream: ReadableStream<Uint8Array>,
  onEvent?: (event: SseEvent) => Promise<void> | void,
): Promise<void> => {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const normalizedBuffer = buffer.replace(/\r\n/g, '\n');
      const blocks = normalizedBuffer.split('\n\n');
      buffer = blocks.pop() ?? '';

      for (const block of blocks) {
        await processEventBlock(block, onEvent);
      }
    }

    buffer += decoder.decode();
    if (buffer.trim().length > 0) {
      await processEventBlock(buffer, onEvent);
    }
  } finally {
    reader.releaseLock();
  }
};

export const connectSse = (path: string, options: ConnectSseOptions = {}): SseConnection => {
  const controller = new AbortController();
  let manuallyClosed = false;

  void (async () => {
    try {
      const response = await fetch(apiUrl(path), {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          ...authHeaders(),
          ...options.headers,
        },
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`SSE request failed with status ${response.status}`);
      }
      if (!response.body) {
        throw new Error('SSE response body is empty');
      }

      await consumeStream(response.body, options.onEvent);
      if (!manuallyClosed) {
        await options.onClose?.();
      }
    } catch (error) {
      if (manuallyClosed || (error instanceof DOMException && error.name === 'AbortError')) {
        return;
      }
      await options.onError?.(toError(error));
    }
  })();

  return {
    close: () => {
      manuallyClosed = true;
      controller.abort();
    },
  };
};
