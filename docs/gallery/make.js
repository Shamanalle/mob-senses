// Renders the store gallery (docs/images/gallery/*.png) from docs/gallery.json with page.html.
// Entries with "cover" become the cover; entries with "heading" become a card around "shot".
// Run from the repo root: NODE_PATH=$(npm root -g) node docs/gallery/make.js
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const { pathToFileURL } = require("url");

const root = path.resolve(__dirname, "../..");
const page = pathToFileURL(path.join(__dirname, "page.html")).href;
const list = JSON.parse(fs.readFileSync(path.join(root, "docs/gallery.json"), "utf8"));
// page.html sits two folders down, so repo paths are given relative to it
const rel = file => "../../" + file;

function query(entry) {
  const q = new URLSearchParams();
  if (entry.cover) {
    const c = entry.cover;
    q.set("cover", "");
    q.set("for", c.for);
    q.set("title", c.heading);
    q.set("text", c.text);
    c.chips.forEach(chip => q.append("chip", chip));
    q.set("shot", rel(c.shot));
    q.set("loaders", c.loaders);
  } else {
    q.set("title", entry.heading);
    q.set("text", entry.text);
    q.set("shot", rel(entry.shot));
  }
  return q.toString();
}

(async () => {
  const browser = await chromium.launch();
  const tab = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  for (const entry of list) {
    if (!entry.cover && !entry.heading) continue;
    await tab.goto(page + "?" + query(entry));
    await tab.evaluate(async () => {
      await document.fonts.ready;
      await Promise.all([...document.images].map(img => img.decode()));
    });
    const out = path.join(root, entry.file);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    await tab.screenshot({ path: out });
    console.log(entry.file);
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
