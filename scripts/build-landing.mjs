import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, 'dist');
const html = readFileSync(resolve(root, 'docs/landing.html'), 'utf8');
const files = new Set([
  'assets/inter.css',
  'assets/inter-variable.ttf',
  'assets/Inter-OFL.txt',
  ...Array.from(html.matchAll(/(?:src|href)="((?:assets|screenshots)\/[^"?#]+)"/g), match => match[1]),
]);

mkdirSync(output, { recursive: true });
writeFileSync(resolve(output, 'index.html'), html);
for (const file of files) {
  const target = resolve(output, file);
  mkdirSync(dirname(target), { recursive: true });
  copyFileSync(resolve(root, 'docs', file), target);
}
writeFileSync(resolve(output, 'vercel.json'), JSON.stringify({ cleanUrls: true }, null, 2) + '\n');
console.log(`Landing preparada em dist/: index.html e ${files.size} recursos públicos.`);
