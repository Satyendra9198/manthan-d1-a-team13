// Builds the demo PDFs from the Markdown docs (Mermaid diagrams rendered) using the frontend's Playwright Chromium.
// Usage (repo root):  node docs/pdf/build-pdf.mjs
// Output: docs/pdf/ReturnRight-Demo-Pack.pdf (all docs) + one PDF per document.
import { createRequire } from "node:module";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const require = createRequire(join(root, "frontend", "package.json"));
const { chromium } = require("@playwright/test");

const DOCS = [
  { file: "docs/DEMO-FLOW.md", title: "Demo Flow", out: "ReturnRight-Demo-Flow.pdf" },
  { file: "docs/RUNBOOK.md", title: "Runbook", out: "ReturnRight-Runbook.pdf" },
  { file: "docs/IDEATION.md", title: "Ideation", out: "ReturnRight-Ideation.pdf" },
  { file: "docs/LLD.md", title: "Low-Level Design", out: "ReturnRight-LLD.pdf" },
];

const css = `
  @page { size: A4; margin: 16mm 14mm; }
  body { font-family: "Segoe UI", Inter, Arial, sans-serif; color: #1f2430; font-size: 10.5pt; line-height: 1.45; }
  h1 { color: #4338ca; font-size: 20pt; border-bottom: 2px solid #4338ca; padding-bottom: 4px; page-break-before: always; }
  h1:first-of-type { page-break-before: avoid; }
  h2 { color: #3730a3; font-size: 14pt; margin-top: 18px; page-break-after: avoid; }
  h3 { font-size: 11.5pt; page-break-after: avoid; }
  table { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 9pt; page-break-inside: auto; }
  tr { page-break-inside: avoid; }
  th, td { border: 1px solid #d5d8e0; padding: 4px 6px; vertical-align: top; text-align: left; }
  th { background: #eef0fb; }
  code { background: #f2f3f7; padding: 0 3px; border-radius: 3px; font-size: 8.8pt; }
  pre { background: #f6f7fa; padding: 8px; border-radius: 4px; overflow-wrap: anywhere; white-space: pre-wrap; font-size: 8.5pt; }
  pre.mermaid { background: none; text-align: center; page-break-inside: avoid; }
  blockquote { border-left: 3px solid #a5b4fc; margin: 8px 0; padding: 2px 10px; color: #4b5263; }
  .cover { height: 250mm; display: flex; flex-direction: column; justify-content: center; }
  .cover h1 { border: none; font-size: 32pt; page-break-before: avoid; }
  .cover p { font-size: 12pt; color: #4b5263; }
`;

function page(sections, subtitle) {
  const docs = JSON.stringify(sections.map((s) => readFileSync(join(root, s.file), "utf8")));
  return `<!doctype html><html><head><meta charset="utf-8"><title>ReturnRight</title><style>${css}</style>
<script src="https://cdn.jsdelivr.net/npm/marked@15.0.6/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"></script></head><body>
<div class="cover"><h1>ReturnRight</h1><p>Retail returns &amp; refunds portal — AI Manthan hackathon, Track A</p>
<p>${subtitle}</p><p>Generated ${new Date().toISOString().slice(0, 10)} from the repository docs. No secrets are included:
passwords are referenced by their <code>.env</code> variable names.</p></div>
<div id="content"></div>
<script>
  const renderer = new marked.Renderer();
  const baseCode = renderer.code.bind(renderer);
  renderer.code = (tok) => tok.lang === "mermaid"
      ? '<pre class="mermaid">' + tok.text.replace(/</g, "&lt;") + '</pre>' : baseCode(tok);
  marked.use({ renderer, gfm: true });
  document.getElementById("content").innerHTML = ${docs}.map((md) => marked.parse(md)).join("");
  mermaid.initialize({ startOnLoad: false, theme: "neutral", flowchart: { useMaxWidth: true }, securityLevel: "strict" });
  mermaid.run({ querySelector: "pre.mermaid" }).then(() => { window.__done = true; },
      (e) => { window.__error = String(e); window.__done = true; });
</script></body></html>`;
}

const browser = await chromium.launch();
try {
  mkdirSync(here, { recursive: true });
  const jobs = [{ sections: DOCS, out: "ReturnRight-Demo-Pack.pdf", subtitle: "Demo Pack: Demo Flow · Runbook · Ideation · LLD" },
    ...DOCS.map((d) => ({ sections: [d], out: d.out, subtitle: d.title }))];
  for (const job of jobs) {
    const html = join(here, ".tmp-" + job.out.replace(".pdf", ".html"));
    writeFileSync(html, page(job.sections, job.subtitle));
    const p = await browser.newPage();
    await p.goto(pathToFileURL(html).href, { waitUntil: "networkidle" });
    await p.waitForFunction(() => window.__done === true, null, { timeout: 120000 });
    const err = await p.evaluate(() => window.__error);
    if (err) console.warn(`  mermaid warning in ${job.out}: ${err}`);
    await p.pdf({ path: join(here, job.out), format: "A4", printBackground: true,
      displayHeaderFooter: true, headerTemplate: "<span></span>",
      footerTemplate: '<div style="font-size:8px;width:100%;text-align:center;color:#888">ReturnRight · <span class="pageNumber"></span>/<span class="totalPages"></span></div>',
      margin: { top: "16mm", bottom: "16mm", left: "14mm", right: "14mm" } });
    await p.close();
    console.log("wrote docs/pdf/" + job.out);
  }
} finally {
  await browser.close();
}
