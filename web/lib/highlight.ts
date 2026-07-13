import hljs from "highlight.js/lib/core";
import java from "highlight.js/lib/languages/java";
import javascript from "highlight.js/lib/languages/javascript";
import typescript from "highlight.js/lib/languages/typescript";
import xml from "highlight.js/lib/languages/xml";
import python from "highlight.js/lib/languages/python";
import json from "highlight.js/lib/languages/json";
import yaml from "highlight.js/lib/languages/yaml";
import css from "highlight.js/lib/languages/css";
import bash from "highlight.js/lib/languages/bash";
import markdown from "highlight.js/lib/languages/markdown";
import go from "highlight.js/lib/languages/go";
import rust from "highlight.js/lib/languages/rust";
import c from "highlight.js/lib/languages/c";
import cpp from "highlight.js/lib/languages/cpp";
import sql from "highlight.js/lib/languages/sql";
import ruby from "highlight.js/lib/languages/ruby";
import php from "highlight.js/lib/languages/php";

hljs.registerLanguage("java", java);
hljs.registerLanguage("javascript", javascript);
hljs.registerLanguage("typescript", typescript);
hljs.registerLanguage("xml", xml);
hljs.registerLanguage("python", python);
hljs.registerLanguage("json", json);
hljs.registerLanguage("yaml", yaml);
hljs.registerLanguage("css", css);
hljs.registerLanguage("bash", bash);
hljs.registerLanguage("markdown", markdown);
hljs.registerLanguage("go", go);
hljs.registerLanguage("rust", rust);
hljs.registerLanguage("c", c);
hljs.registerLanguage("cpp", cpp);
hljs.registerLanguage("sql", sql);
hljs.registerLanguage("ruby", ruby);
hljs.registerLanguage("php", php);

const EXTENSION_LANGUAGE: Record<string, string> = {
  java: "java",
  js: "javascript",
  jsx: "javascript",
  mjs: "javascript",
  cjs: "javascript",
  ts: "typescript",
  tsx: "typescript",
  html: "xml",
  htm: "xml",
  xml: "xml",
  py: "python",
  json: "json",
  yml: "yaml",
  yaml: "yaml",
  css: "css",
  scss: "css",
  sh: "bash",
  bash: "bash",
  md: "markdown",
  markdown: "markdown",
  go: "go",
  rs: "rust",
  c: "c",
  h: "c",
  cpp: "cpp",
  cc: "cpp",
  hpp: "cpp",
  sql: "sql",
  rb: "ruby",
  php: "php",
};

export function languageForPath(path: string): string | null {
  const ext = path.split(".").pop()?.toLowerCase();
  if (!ext) {
    return null;
  }
  return EXTENSION_LANGUAGE[ext] ?? null;
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/**
 * Rendering-complexity rationale (frontend spec, section 1): highlights one line at
 * a time rather than tokenizing the whole file and splitting the resulting HTML by
 * newline. Splitting would break whenever a token spans a line boundary (an
 * unterminated multi-line comment or string), since an opening {@code <span>}
 * would not close on the same line it started, corrupting every line after it.
 * Per-line highlighting sacrifices that cross-line context (a multi-line block
 * comment will not stay highlighted as a comment past its first line) in exchange
 * for correctness of the line-by-line rendering the rest of this app already
 * assumes (line numbers, the blame gutter, review line-anchoring). Named here
 * rather than silently accepted, the same discipline as the backend's own
 * documented tradeoffs.
 */
export function highlightLine(line: string, language: string | null): string {
  if (!language) {
    return escapeHtml(line);
  }
  try {
    return hljs.highlight(line, { language, ignoreIllegals: true }).value;
  } catch {
    return escapeHtml(line);
  }
}
