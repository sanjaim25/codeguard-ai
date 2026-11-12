import {afterEach, describe, expect, it, vi} from 'vitest';
import {api, ApiError} from './client';

afterEach(() => vi.unstubAllGlobals());
describe('same-origin API client', () => {
  it('reads data with the session cookie', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({name:'CodeGuard AI'})));
    vi.stubGlobal('fetch',fetch);
    expect(await api('/api/system')).toEqual({name:'CodeGuard AI'});
    expect(fetch).toHaveBeenCalledWith('/api/system',expect.objectContaining({credentials:'same-origin'}));
  });
  it('retrieves and sends CSRF protection for mutations', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'test-token'})))
      .mockResolvedValueOnce(new Response(null,{status:204}));
    vi.stubGlobal('fetch',fetch);
    await api('/api/logout',{method:'POST'});
    expect(fetch.mock.calls[1][1].headers.get('X-CSRF-TOKEN')).toBe('test-token');
  });
  it('surfaces safe problem details and handles non-JSON failures', async () => {
    vi.stubGlobal('fetch',vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({detail:'Repository not found'}),{status:404}))
      .mockResolvedValueOnce(new Response('upstream unavailable',{status:502})));
    await expect(api('/api/repositories/unknown')).rejects.toThrow('Repository not found');
    await expect(api('/api/system')).rejects.toBeInstanceOf(ApiError);
  });
  it('does not send session headers or cookies to arbitrary URLs', async () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch',fetch);
    await expect(api('https://example.com')).rejects.toThrow('same-origin');
    expect(fetch).not.toHaveBeenCalled();
  });
});
