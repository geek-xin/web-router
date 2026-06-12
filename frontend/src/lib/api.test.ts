import { describe, expect, it, vi } from 'vitest';
import { fetchJson } from './api';

describe('fetchJson', () => {
  it('throws business errors even when HTTP status is 200', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ success: false, message: '名称重复' }), { status: 200 })));

    await expect(fetchJson('/admin/api/routes')).rejects.toThrow('名称重复');
  });
});
