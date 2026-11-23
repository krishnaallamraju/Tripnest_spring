import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

function client(fetch, env = { NEXT_PUBLIC_API_URL: 'https://api.example.test/' }) {
  const values = new Map([['tripnest_token', 'saved-token'], ['tripnest_email', 'user@example.test'], ['user', '{}']]);
  const events = [];
  let timeout;
  let cleared = false;
  let delay;
  const context = {
    exports: {}, process: { env }, fetch, Headers, FormData, AbortController, AbortSignal,
    Event, window: { dispatchEvent: event => events.push(event.type) },
    localStorage: { getItem: key => values.get(key) || null, removeItem: key => values.delete(key) },
    setTimeout: (callback, ms) => { timeout = callback; delay = ms; return 1; },
    clearTimeout: () => { cleared = true; },
  };
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('app/lib/api.ts', 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, context);
  return { api: context.exports.api, values, events, expire: () => timeout(), delay: () => delay, cleared: () => cleared };
}

test('protected 401 clears the stale session and requests sign-in', async () => {
  const c = client(async () => Response.json({ error: 'Authentication required' }, { status: 401 }));
  await assert.rejects(c.api('/api/notifications'), /session has expired/);
  assert.equal(c.values.has('tripnest_token'), false);
  assert.equal(c.values.has('user'), false);
  assert.deepEqual(c.events, ['tripnest:session-expired']);
});

test('invalid login does not clear an existing session', async () => {
  const c = client(async () => Response.json({ message: 'Invalid email or password' }, { status: 401 }));
  await assert.rejects(c.api('/api/auth/login'), /Invalid email or password/);
  assert.equal(c.values.get('tripnest_token'), 'saved-token');
  assert.deepEqual(c.events, []);
});

test('late 401 cannot clear a newer session', async () => {
  const c = client(async () => {
    c.values.set('tripnest_token', 'new-session-token');
    return Response.json({}, { status: 401 });
  });
  await assert.rejects(c.api('/api/notifications'), /session has expired/);
  assert.equal(c.values.get('tripnest_token'), 'new-session-token');
  assert.deepEqual(c.events, []);
});

test('login allows backend startup and omits stale credentials', async () => {
  const c = client(async (url, options) => {
    assert.equal(url, 'https://api.example.test/api/auth/login');
    assert.equal(options.headers.has('Authorization'), false);
    return Response.json({ token: 'new-token' });
  });
  assert.equal((await c.api('/api/auth/login', { method: 'POST' })).token, 'new-token');
  assert.equal(c.delay(), 90000);
  assert.equal(c.cleared(), true);
});

test('missing production configuration never contacts localhost', async () => {
  const c = client(() => assert.fail('must not fetch'), { NODE_ENV: 'production' });
  await assert.rejects(c.api('/api/auth/login'), /not configured/);
});

test('preserves Headers overrides and existing query parameters', async () => {
  const c = client(async (url, options) => {
    assert.equal(url, 'https://api.example.test/trips/search?name=Paris&email=user%40example.test');
    assert.equal(options.headers.get('Authorization'), 'Bearer override');
    return new Response(null, { status: 204 });
  });
  assert.equal(await c.api('/trips/search?name=Paris', { headers: new Headers({ Authorization: 'Bearer override' }) }), undefined);
});

test('timeout still applies when the caller supplies a signal; writes are not retried', async () => {
  let calls = 0;
  const c = client((_url, options) => {
    calls++;
    return new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(options.signal.reason)));
  });
  const pending = c.api('/trips', { method: 'POST', signal: new AbortController().signal });
  c.expire();
  await assert.rejects(pending, /taking too long/);
  assert.equal(calls, 1);
  assert.equal(c.cleared(), true);
});

test('timeout covers a stalled response body', async () => {
  const c = client(async (_url, options) => ({
    text: () => new Promise((_resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(options.signal.reason));
      c.expire();
    }),
  }));
  await assert.rejects(c.api('/trips/my'), /taking too long/);
});

test('HTML gateway errors are not shown as markup', async () => {
  const c = client(async () => new Response('<html>Bad Gateway</html>', { status: 502 }));
  await assert.rejects(c.api('/trips/my'), /temporarily unavailable/);
});

test('API validation messages are retained', async () => {
  const c = client(async () => Response.json({ message: 'Invalid email or password' }, { status: 401 }));
  await assert.rejects(c.api('/api/auth/login'), /Invalid email or password/);
});
