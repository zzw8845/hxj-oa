#!/usr/bin/env node
/**
 * 前端原型页静态自检：捕获"删了定义但引用还在"这类运行时才会暴露的错误。
 *
 * 用法：node oa-frontend/tools/check-frontend.mjs
 *
 * 检查项：
 *  1. 悬空引用：被引用但从未声明的标识符（Uncaught ReferenceError 的来源）
 *  2. 模板作用域：模板里调用了但未通过 setup return 暴露的成员（is not a function 的来源）
 *  3. 语法：两个内联 script 的 JS 语法（调用 node --check）
 *
 * 退出码非 0 表示发现问题，可直接接入 CI 或提交前检查。
 */
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const file = join(root, 'public', '海峡金OA审批系统原型（改，对接后端）.html');
const html = readFileSync(file, 'utf8');

const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
if (scripts.length === 0) {
  console.error('未找到内联 script，检查文件路径是否正确');
  process.exit(1);
}

const problems = [];

// ── 1. 语法检查 ──
const dir = mkdtempSync(join(tmpdir(), 'fe-check-'));
scripts.forEach((code, i) => {
  const p = join(dir, `script-${i}.js`);
  writeFileSync(p, code);
  try {
    execFileSync('node', ['--check', p], { stdio: 'pipe' });
  } catch (e) {
    problems.push(`[语法] script#${i} 存在语法错误：${String(e.stderr).split('\n').slice(-4).join(' ')}`);
  }
});

// ── 2. 声明集合 ──
const declared = new Set();
for (const code of scripts) {
  for (const m of code.matchAll(/\b(?:const|let|var|function|class)\s+([A-Za-z_$][\w$]*)/g)) declared.add(m[1]);
  for (const m of code.matchAll(/\b(?:const|let|var)\s*\{([^}]*)\}/g))
    m[1].split(',').forEach(part => {
      const n = part.split(':').pop().split('=')[0].trim();
      if (/^[A-Za-z_$][\w$]*$/.test(n)) declared.add(n);
    });
  for (const m of code.matchAll(/function\s*[A-Za-z_$\w]*\s*\(([^)]*)\)/g))
    m[1].split(',').forEach(part => {
      const n = part.split('=')[0].trim();
      if (/^[A-Za-z_$][\w$]*$/.test(n)) declared.add(n);
    });
}

// ── 3. 悬空引用：重点检查"曾经被声明过、后来定义被删"的名字 ──
//    通用启发式：脚本里出现在 `.content.querySelector`、`?.content` 等补丁模式前、且未声明的标识符
const danglingPatterns = [
  /\b([A-Za-z_$][\w$]*)\?\.content\b/g, // 补丁常见形态：xxx?.content.querySelector(...)
  /\b([A-Za-z_$][\w$]*)\?\.querySelector\b/g,
];
const dangling = new Set();
for (const code of scripts) {
  for (const re of danglingPatterns) {
    for (const m of code.matchAll(re)) {
      const name = m[1];
      if (!declared.has(name) && name !== 'this') dangling.add(name);
    }
  }
}
// 已知外部全局白名单（CDN 引入）
const externals = new Set(['Vue', 'ElementPlus', 'ElementPlusLocaleZhCn']);
[...dangling].filter(n => !externals.has(n)).forEach(n =>
  problems.push(`[悬空引用] "${n}" 被引用但从未声明 → 运行时会抛 ReferenceError`));

// ── 4. 模板作用域：模板里调用的函数是否被 setup return 暴露 ──
const appStart = html.indexOf('<div id="app"');
const tpl = appStart >= 0 ? html.slice(appStart, html.indexOf('</body>')) : '';
const mainCode = scripts[scripts.length - 1] ?? '';

const definedInSetup = new Set();
for (const m of mainCode.matchAll(/\b(?:const|let|var|function)\s+([A-Za-z_$][\w$]*)/g)) definedInSetup.add(m[1]);

const returnIdx = mainCode.lastIndexOf('return {');
const returned = new Set();
if (returnIdx > 0) {
  const body = mainCode.slice(returnIdx, mainCode.indexOf('}', returnIdx) + 1);
  for (const m of body.matchAll(/[A-Za-z_$][\w$]*/g)) returned.add(m[0]);
}

const calledInTemplate = new Set();
for (const m of tpl.matchAll(/\{\{([^}]*)\}\}/g))
  for (const f of m[1].matchAll(/\b([A-Za-z_$][\w$]*)\s*\(/g)) calledInTemplate.add(f[1]);
for (const m of tpl.matchAll(/(?:@click|@change|v-if|v-else-if|v-for|:label|:value|:model-value)="([^"]*)"/g))
  for (const f of m[1].matchAll(/\b([A-Za-z_$][\w$]*)\s*\(/g)) calledInTemplate.add(f[1]);

const builtins = new Set(['String', 'Number', 'Boolean', 'Array', 'Object', 'Math', 'Date', 'JSON',
  'parseInt', 'parseFloat', 'isNaN', 'if', 'for', 'return', 'filter', 'map', 'join', 'push', 'splice', 'in', 'of',
  'includes', 'indexOf', 'slice', 'replace', 'split', 'trim', 'toString', 'valueOf', 'hasOwnProperty']);
for (const name of calledInTemplate) {
  if (builtins.has(name) || name.startsWith('$')) continue;
  const accessible = returned.has(name) || externals.has(name);
  if (!accessible && definedInSetup.has(name)) {
    problems.push(`[模板作用域] 模板调用了 "${name}"，但未在 setup return 中暴露 → 运行时报 is not a function`);
  }
}

// ── 5. 旧契约残留（图模型重构后的回归保护）──
const legacy = ['conditionRules', 'assigneeRole', 'submitFormVars', 'matchedFlow', '__cfv'];
for (const word of legacy) {
  const count = (html.match(new RegExp(`\\b${word}\\b`, 'g')) || []).length;
  if (count > 0) problems.push(`[旧契约残留] "${word}" 出现 ${count} 次（图模型重构后应清零）`);
}

if (problems.length === 0) {
  console.log('✅ 前端自检通过（语法 / 悬空引用 / 模板作用域 / 旧契约残留）');
  process.exit(0);
}
console.error(`❌ 发现 ${problems.length} 个问题：`);
problems.forEach(p => console.error('  - ' + p));
process.exit(1);
