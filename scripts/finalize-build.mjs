import { readdir, writeFile } from "node:fs/promises";
const paths = [];
async function walk(dir, prefix = "") {
  for (const e of await readdir(dir, { withFileTypes: true })) {
    const p = prefix + "/" + e.name;
    if (e.isDirectory()) await walk(dir + "/" + e.name, p);
    else if (/\.(js|css|woff2?)$/.test(e.name)) paths.push(p);
  }
}
await walk("dist/client");
await writeFile("dist/client/offline-assets.json", JSON.stringify(paths));
console.log(`Offline precache manifest: ${paths.length} assets`);
