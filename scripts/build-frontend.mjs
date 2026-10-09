import { copyFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(
    root,
    "node_modules/lightweight-charts/dist/lightweight-charts.standalone.production.js");
const destination = resolve(
    root,
    "src/main/resources/static/vendor/lightweight-charts.standalone.production.js");

await mkdir(dirname(destination), { recursive: true });
await copyFile(source, destination);
console.log("Lightweight Charts copiato negli asset statici.");
