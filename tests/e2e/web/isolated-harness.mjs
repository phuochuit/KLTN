// Refuse legacy mutation-capable browser checks unless the loopback listener belongs
// to the explicitly opted-in LoginBrowserHarnessTest process tree for this run.
import { realpath, readFile, access } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

export async function assertIsolatedHarness(root, runArgument) {
  if (!runArgument) throw new Error('Supply a fresh active isolated harness run directory.');
  const runs = await realpath(path.join(root, '.artifacts/test-runs'));
  const run = await realpath(path.resolve(runArgument));
  if (!run.startsWith(runs + path.sep)) throw new Error('Harness run must be below .artifacts/test-runs.');
  try { await access(path.join(run, 'stop')); throw new Error('Harness has stopped.'); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  const port = (await readFile(path.join(run, 'server.port'), 'utf8')).trim();
  const controller = (await readFile(path.join(run, 'controller.pid'), 'utf8')).trim();
  if (!/^\d{1,5}$/.test(port) || Number(port) < 1 || Number(port) > 65535 || !/^\d+$/.test(controller))
    throw new Error('Invalid isolated harness markers.');
  const script = `
$ErrorActionPreference = 'Stop'
$owner = Get-Process -Id ${Number(controller)} -ErrorAction Stop
$processes = @(Get-CimInstance Win32_Process)
$listener = @(Get-NetTCPConnection -State Listen -LocalPort ${Number(port)} -ErrorAction Stop)
if (!$listener.Count) { exit 2 }
$runPath = $env:LUMIPARK_HARNESS_RUN.Replace('/', '\\').ToLowerInvariant()
foreach ($socket in $listener) {
  $currentId = [int]$socket.OwningProcess
  $verifiedHarness = $false
  for ($depth = 0; $depth -lt 12 -and $currentId -gt 0; $depth++) {
    $entry = $processes | Where-Object { $_.ProcessId -eq $currentId } | Select-Object -First 1
    if (!$entry) { break }
    $command = [string]$entry.CommandLine
    $normalized = $command.Replace('/', '\\').ToLowerInvariant()
    if ($command -match 'LoginBrowserHarnessTest' -and
        $command -match 'parking.test.login-browser.run-directory' -and
        $normalized.Contains($runPath)) { $verifiedHarness = $true }
    if ($currentId -eq ${Number(controller)}) {
      if ($verifiedHarness) { exit 0 }
      break
    }
    $currentId = [int]$entry.ParentProcessId
  }
}
exit 2`;
  try {
    execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], {
      stdio: 'ignore', timeout: 10000, windowsHide: true,
      env: { ...process.env, LUMIPARK_HARNESS_RUN: run },
    });
  } catch { throw new Error('Listener ownership does not prove an active isolated LoginBrowserHarnessTest; refusing browser mutations.'); }
  return { run, port };
}
