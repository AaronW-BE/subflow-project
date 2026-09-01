// Copies the built console into internal/static/dist, which is what the Go
// binary embeds. Without this the server would keep serving the previous build.
import { cpSync, rmSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(here, '..', 'dist');
const target = resolve(here, '..', '..', 'internal', 'static', 'dist');

if (!existsSync(source)) {
  console.error('sync-embed: no dist/ to copy - run vite build first');
  process.exit(1);
}

rmSync(target, { recursive: true, force: true });
cpSync(source, target, { recursive: true });
console.log(`sync-embed: copied dist -> ${target}`);
