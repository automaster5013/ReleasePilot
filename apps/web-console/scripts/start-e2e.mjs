import { cpSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = fileURLToPath(new URL("../", import.meta.url));
const standalone = path.join(root, ".next", "standalone");
if (!existsSync(path.join(standalone, "server.js"))) throw new Error("Run npm run build before E2E");
// Match Docker's standalone packaging; only generated build assets are copied.
cpSync(path.join(root, ".next", "static"), path.join(standalone, ".next", "static"), { recursive: true });
if (existsSync(path.join(root, "public"))) cpSync(path.join(root, "public"), path.join(standalone, "public"), { recursive: true });
process.env.PORT = "3100";
process.env.HOSTNAME = "127.0.0.1";
await import(new URL("../.next/standalone/server.js", import.meta.url).href);
