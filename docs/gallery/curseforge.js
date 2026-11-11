// Renders the pictures of the CurseForge descriptions of the mod and the plugin
// (docs/images/curseforge/*.png): section headings and facts strips from banner.html, and 840 px
// copies of the cover and screenshots. CurseForge refuses pictures 850 px wide or wider.
// Also renders the plugin's cover and the GitHub social preview (social.html, docs/images/social-preview.png),
// set by hand in the repository's Settings → General.
// Run from the repo root: NODE_PATH=$(npm root -g) node docs/gallery/curseforge.js
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const { pathToFileURL } = require("url");

const root = path.resolve(__dirname, "../..");
const out = name => path.join(root, "docs/images/curseforge", name + ".png");
const banner = pathToFileURL(path.join(__dirname, "banner.html")).href;

const headings = {
  senses: { title: "How mobs sense you", text: "Sound waves through walls, firearms & suppressors, voice chat, torchlight and blood scent.", accent: "#f5b841" },
  players: { title: "For players", text: "Tactical Stealth HUD, sleek 2-tab settings GUI, keybind and /mshud on any server.", accent: "#3fd0e6" },
  servers: { title: "For servers", text: "Presets, per-world balance, fair-play HUD lock, clickable /mobsenses and live OP sync.", accent: "#58d68d" },
  files: { title: "Which file do I need?", text: "One universal file per loader for Minecraft 1.16.5 – 26.3.", accent: "#a98bff" },
  // The plugin's page (docs/curseforge-plugin.html)
  "plugin-senses": { title: "How mobs sense players", text: "Footsteps, blocks, voice chat, torchlight and blood trails — vanilla clients need nothing.", accent: "#f5b841" },
  "plugin-admin": { title: "Tools for admins", text: "Presets, per-world settings, clickable /mobsenses, staff bypass and live client sync.", accent: "#58d68d" },
  "plugin-install": { title: "Install", text: "One universal jar for Paper, Purpur, Folia and Spigot 1.16.5 – 26.3.", accent: "#a98bff" },
};
const facts = [
  "Install on|server, client or both",
  "Loaders|Fabric · Quilt · Forge · NeoForge · Paper",
  "Minecraft|1.16.5 – 26.3",
];
const pluginFacts = [
  "Players need|nothing (vanilla clients join)",
  "Servers|Paper · Purpur · Folia · Spigot",
  "Minecraft|1.16.5 – 26.3",
];
// The plugin's cover, in the gallery's size for its CurseForge gallery and as an 840 px copy
const pluginCover = {
  for: "Server Plugin · Vanilla Clients Need Nothing",
  title: "Mob Senses",
  text: "Mobs hear steps, blocks & Simple Voice Chat through walls, spot torchlight vs darkness, and track blood trails on Paper, Purpur & Folia.",
  chips: ["No client mods needed", "Wall occlusion", "Simple Voice Chat", "Torchlight & darkness", "Blood trails", "Per-world presets", "Clickable /mobsenses", "Fair-play HUD lock", "Folia ready"],
  shot: "docs/images/ui-server.png",
  loaders: "<b>Paper · Purpur · Folia · Spigot</b> &nbsp; Minecraft 1.16.5 – 26.3 &nbsp; Players need nothing",
};
const copies = {
  cover: "docs/images/gallery/cover.png",
  "plugin-cover": "docs/images/gallery/cover-plugin.png",
  banner: "docs/images/social-preview.png",
  hud: "docs/images/ui-hud.png",
  hearing: "docs/images/ui-hearing.png",
  sight: "docs/images/ui-sight.png",
  server: "docs/images/ui-server.png",
  debug: "docs/images/ui-debug.png",
};

(async () => {
  fs.mkdirSync(path.join(root, "docs/images/curseforge"), { recursive: true });
  fs.mkdirSync(path.join(root, "docs/images/gallery"), { recursive: true });
  const browser = await chromium.launch();
  const big = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  const cq = new URLSearchParams({ cover: "", for: pluginCover.for, title: pluginCover.title, text: pluginCover.text,
    shot: "../../" + pluginCover.shot, loaders: pluginCover.loaders });
  pluginCover.chips.forEach(chip => cq.append("chip", chip));
  await big.goto(pathToFileURL(path.join(__dirname, "page.html")).href + "?" + cq);
  await big.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(i => i.decode())); });
  await big.screenshot({ path: path.join(root, "docs/images/gallery/cover-plugin.png") });

  const small = await browser.newPage({ viewport: { width: 1280, height: 640 } });
  await small.goto(pathToFileURL(path.join(__dirname, "social.html")).href);
  await small.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(i => i.decode())); });
  await small.screenshot({ path: path.join(root, "docs/images/social-preview.png") });

  // The same banner in 16:9 for the Modrinth and CurseForge galleries, which show pictures in that shape
  const wide = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 });
  await wide.goto(pathToFileURL(path.join(__dirname, "social.html")).href + "?h=720");
  await wide.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(i => i.decode())); });
  await wide.screenshot({ path: path.join(root, "docs/images/gallery/banner.png") });

  const tab = await browser.newPage({ viewport: { width: 840, height: 600 } });
  const shoot = async (query, name) => {
    await tab.goto(banner + "?" + query);
    await tab.evaluate(() => document.fonts.ready);
    await tab.locator(".box").screenshot({ path: out(name), omitBackground: true });
  };
  for (const [name, h] of Object.entries(headings)) await shoot(new URLSearchParams(h).toString(), "heading-" + name);
  const q = new URLSearchParams({ facts: "", accent: "#f5b841" });
  facts.forEach(f => q.append("fact", f));
  await shoot(q.toString(), "facts");
  const pq = new URLSearchParams({ facts: "", accent: "#58d68d" });
  pluginFacts.forEach(f => pq.append("fact", f));
  await shoot(pq.toString(), "plugin-facts");
  for (const [name, file] of Object.entries(copies)) {
    await tab.setContent(`<img src="${pathToFileURL(path.join(root, file)).href}" style="display:block;width:840px">`);
    await tab.evaluate(() => document.images[0].decode());
    await tab.locator("img").screenshot({ path: out(name) });
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
