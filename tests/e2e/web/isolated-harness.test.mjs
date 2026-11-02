import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { once } from 'node:events';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertIsolatedHarness } from './isolated-harness.mjs';

const workspace = fileURLToPath(new URL('../../../', import.meta.url));
const fixtureRoot = path.join(workspace, '.artifacts/test-runs', `${new Date().toISOString().replace(/[^0-9]/g,'')}-harness-guard-${process.pid}`);
const runs = path.join(fixtureRoot, '.artifacts/test-runs');
await mkdir(runs, { recursive: true });
const makeRun = async name => { const run = path.join(runs, name); await mkdir(run); return run; };

test('refuses an omitted fixture before browser startup', async () => {
  await assert.rejects(assertIsolatedHarness(fixtureRoot), /Supply a fresh/);
});
test('refuses a real directory outside the fixture run root', async () => {
  await assert.rejects(assertIsolatedHarness(fixtureRoot, fixtureRoot), /must be below/);
});
test('refuses a stopped fixture even if markers might otherwise look valid', async () => {
  const run = await makeRun('stopped'); await writeFile(path.join(run,'stop'),'');
  await assert.rejects(assertIsolatedHarness(fixtureRoot,run), /has stopped/);
});
test('refuses an invalid listener port', async () => {
  const run = await makeRun('bad-port');
  await writeFile(path.join(run,'server.port'),'65536'); await writeFile(path.join(run,'controller.pid'),String(process.pid));
  await assert.rejects(assertIsolatedHarness(fixtureRoot,run), /Invalid isolated/);
});
test('refuses a live unrelated loopback server despite fresh markers and a live PID', async () => {
  const server = createServer(socket => socket.end()); server.listen(0,'127.0.0.1'); await once(server,'listening');
  try {
    const run = await makeRun('unrelated-listener');
    await writeFile(path.join(run,'server.port'),String(server.address().port)); await writeFile(path.join(run,'controller.pid'),String(process.pid));
    await assert.rejects(assertIsolatedHarness(fixtureRoot,run), /Listener ownership/);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
