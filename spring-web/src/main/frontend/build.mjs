import { mkdir, copyFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const output = path.join(process.argv[2] ?? path.join(root, 'target/classes'), 'static');
await mkdir(path.join(output, 'css'), { recursive: true });
for (const entry of ['web-ui', 'dashboard-ui', 'security-roles', 'account-management', 'registration', 'cards', 'pricing', 'payments']) {
  const result = spawnSync(process.execPath, [
    path.join(root, 'node_modules/@tailwindcss/cli/dist/index.mjs'),
    '-i', path.join(root, `src/main/frontend/styles/${entry}.css`),
    '-o', path.join(output, `css/${entry}.css`), '--minify',
  ], { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
const icons = path.join(output, 'images/lucide');
await mkdir(icons, { recursive: true });
for (const name of ['eye', 'eye-off', 'arrow-right', 'shield-check', 'circle-alert', 'circle-check',
  'panels-top-left', 'scan-line', 'route', 'log-in', 'user-round', 'lock-keyhole',
  'menu', 'clock-3', 'car-front', 'bike', 'house', 'wallet', 'layout-dashboard', 'clipboard-list',
  'search', 'plus', 'refresh-cw', 'circle-help', 'lock-keyhole', 'camera', 'image', 'check',
  'credit-card', 'map', 'history', 'receipt', 'log-out', 'x']) {
  await copyFile(path.join(root, `node_modules/lucide-static/icons/${name}.svg`), path.join(icons, `${name}.svg`));
}
await copyFile(path.join(root, 'node_modules/lucide-static/LICENSE'), path.join(icons, 'LICENSE'));
