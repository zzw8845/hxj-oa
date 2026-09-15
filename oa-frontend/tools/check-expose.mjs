#!/usr/bin/env node
/**
 * 前端 setup 暴露自检。
 *
 * 背景：`public/` 下的原型页用内联 Vue 模板（`createApp({ setup() {...} })`），
 * 模板只能访问 `setup()` 的 return 暴露的名字。新增函数/常量若忘了加入 return，
 * **编译期完全看不出来**，只会在运行时抛 `xxx is not defined`（页面白屏）。
 * 同类遗漏已踩过三次，故做成静态自检。
 *
 * 判定规则（保守：宁漏报、不误报）：
 *   模板中引用 + 脚本顶层声明过（4 空格缩进的 function/const/let）+ 不在任何 setup 的 return 里 → 报告
 *
 * 误报控制：
 *   - 只认顶层声明 → 函数参数、箭头函数内的临时变量（a/b/i…）不会命中；
 *   - return 取<b>所有</b> setup 的并集（页面有登录页 + 主应用两个 setup）；
 *   - v-for 的局部别名在模板提取阶段剔除；
 *   - JS 内置与关键字白名单。
 *
 * 用法：
 *   node tools/check-expose.mjs                      # 检查 public/ 下所有 html
 *   node tools/check-expose.mjs public/xxx.html      # 只检查指定文件
 * 退出码：0 = 通过，1 = 存在未暴露引用
 */
import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const publicDir = join(here, '..', 'public')

const targets = process.argv.slice(2).length
  ? process.argv.slice(2)
  : readdirSync(publicDir)
      .filter((name) => name.endsWith('.html'))
      .map((name) => join(publicDir, name))

const BUILTINS = new Set([
  'true', 'false', 'null', 'undefined', 'typeof', 'in', 'of', 'new', 'return',
  'Math', 'Number', 'String', 'Boolean', 'Array', 'Object', 'JSON', 'Date', 'Set',
  'parseInt', 'parseFloat', 'isNaN', 'console', 'window', 'document',
  'encodeURIComponent', 'decodeURIComponent',
])

/** 从 openIdx 处的 '{' 起返回其配平的 {...} 片段。 */
function sliceBalanced(src, openIdx) {
  let depth = 0
  for (let i = openIdx; i < src.length; i += 1) {
    if (src[i] === '{') depth += 1
    else if (src[i] === '}') {
      depth -= 1
      if (depth === 0) return src.slice(openIdx, i + 1)
    }
  }
  return src.slice(openIdx)
}

/** 所有 setup() 的 return 暴露名（并集）。 */
function exposedNames(src) {
  const names = new Set()
  const re = /setup\s*\(\s*\)\s*\{/g
  let m
  while ((m = re.exec(src)) !== null) {
    const body = sliceBalanced(src, src.indexOf('{', m.index))
    const ret = body.match(/return\s*\{/)
    if (!ret) continue
    const block = sliceBalanced(body, body.indexOf('{', ret.index))
    block
      .replace(/^\{/, '')
      .replace(/\}$/, '')
      .split(',')
      .map((piece) => piece.split(':')[0].replace(/[\s{}[\].]/g, ''))
      .filter((piece) => /^[A-Za-z_$][\w$]*$/.test(piece))
      .forEach((piece) => names.add(piece))
  }
  return names
}

/** 脚本顶层声明的函数/常量名（4 空格缩进 = setup 内顶层）。 */
function declaredNames(src) {
  const names = new Set()
  const re = /\n {4}(?:function\s+([A-Za-z_$][\w$]*)\s*\(|(?:const|let)\s+([A-Za-z_$][\w$]*)\s*=)/g
  let m
  while ((m = re.exec(src)) !== null) {
    names.add(m[1] || m[2])
  }
  return names
}

/** 模板中实际引用的标识符（{{ }} 与指令属性表达式）。 */
function templateIdentifiers(src) {
  const expressions = []
  for (const m of src.matchAll(/\{\{([\s\S]*?)\}\}/g)) expressions.push(m[1])
  for (const m of src.matchAll(/\s(?:v-if|v-else-if|v-show|v-model|v-for|v-bind|@[\w.:-]+|:[\w.-]+)="([^"]*)"/g)) {
    expressions.push(m[1])
  }
  const names = new Set()
  for (const expr of expressions) {
    const locals = new Set()
    for (const alias of expr.matchAll(/(?:\(([^)]*)\)|([A-Za-z_$][\w$]*))\s+(?:in|of)\s/g)) {
      for (const piece of (alias[1] || alias[2] || '').split(',')) {
        const name = piece.split(':')[0].trim()
        if (name) locals.add(name)
      }
    }
    for (const m of expr.matchAll(/(?:^|[^.\w$'"])([A-Za-z_$][\w$]*)/g)) {
      if (!locals.has(m[1])) names.add(m[1])
    }
  }
  return names
}

let failed = false

for (const file of targets) {
  let src
  try {
    src = readFileSync(file, 'utf8')
  } catch {
    console.log(`跳过（读取失败）：${file}`)
    continue
  }
  const exposed = exposedNames(src)
  if (exposed.size === 0) {
    console.log(`跳过（未找到 setup return）：${file}`)
    continue
  }
  const declared = declaredNames(src)
  const missing = [...templateIdentifiers(src)]
    .filter((name) => declared.has(name) && !exposed.has(name) && !BUILTINS.has(name))
    .sort()

  if (missing.length) {
    failed = true
    console.log(`✗ ${file}`)
    console.log('  模板引用了顶层已声明、但未在 setup() return 暴露的名字（运行时报 xxx is not defined）：')
    for (const name of missing) console.log(`    - ${name}`)
  } else {
    console.log(`✓ ${file}`)
  }
}

console.log(failed ? '\n自检失败：请把上面列出的名字加入 setup() 的 return。' : '\n自检通过：模板引用与 setup 暴露一致。')
process.exit(failed ? 1 : 0)
