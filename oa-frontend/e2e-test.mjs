import { chromium } from 'playwright'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const BASE = 'http://localhost:5173/' + encodeURIComponent('海峡金OA审批系统原型.html')
const API_ORIGIN = 'http://localhost:8080'
const results = []

function log(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`${ok ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext()
  const page = await context.newPage()

  const consoleErrors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text())
  })
  page.on('pageerror', (err) => consoleErrors.push(err.message))

  // 接口级断言：任何返回 success=false 的 /api 请求都记为失败。
  // 这是唯一能穿透「UI 断言过松」的防线——页面 catch 掉错误只弹 ElMessage，
  // 既不产生 pageerror，也不影响 DOM，只有响应体能暴露真实结果。
  const apiFailures = []
  const apiCalls = []
  page.on('console', (msg) => {
    console.log('[browser]', msg.type(), msg.text())
  })

  page.on('response', (res) => {
    const url = res.url()
    if (!url.includes('/api/')) return
    const path = url.slice(url.indexOf('/api/')).split('?')[0]
    const method = res.request().method()
    apiCalls.push({ method, path })
    res.json()
      .then((body) => {
        if (body && body.success === false) {
          apiFailures.push(`${method} ${path} → ${body.code}: ${body.message || ''}`)
        }
      })
      .catch(() => { /* 非 JSON 响应（如附件下载）忽略 */ })
  })

  try {
    // === 1. 登录 ===
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForTimeout(5000)

    let loginInput = null
    try {
      loginInput = await page.waitForSelector('#login-app input[type="text"]', { timeout: 5000 })
    } catch {
      try {
        loginInput = await page.waitForSelector('#login-app .el-input__inner', { timeout: 3000 })
      } catch {
        // fallback
      }
    }

    if (loginInput) {
      await loginInput.fill('linanran')
      const pwdInput = await page.$('#login-app input[type="password"]')
      if (pwdInput) await pwdInput.fill('password')
      await page.click('#login-app button:has-text("登 录")')
      await page.waitForTimeout(2000)
    }

    const loginOk = await page.locator('#app').evaluate(el => el.style.display !== 'none').catch(() => false)
    log('登录成功', loginOk, '设计图直连后端')

    if (!loginOk) {
      throw new Error('登录失败，停止测试')
    }

    // === 2. 首页统计 ===
    await page.waitForTimeout(1000)
    const statCards = await page.locator('.stats .el-card').count()
    log('首页统计卡片', statCards > 0, `${statCards} 个卡片`)

    // === 3. 待我审批列表 ===
    await page.click('text=待我审批')
    await page.waitForSelector('.el-table__row', { timeout: 5000 })
    const rowCount = await page.locator('.el-table__row').count()
    log('待我审批列表', rowCount > 0, `${rowCount} 行数据`)

    // === 4. 单据详情（注意：原型页直接取列表对象，不调 /documents/{id}）===
    await page.click('.el-table__row:first-child .el-button:has-text("进入审批")')
    await page.waitForSelector('.el-descriptions', { timeout: 5000 })
    const docCode = (await page.locator('.el-descriptions__body').first().textContent()) || ''
    log('单据详情渲染', docCode.trim().length > 0, docCode.trim().slice(0, 40) || '详情为空')

    // === 5. 审批操作（通过）：先上传凭证，再审批 ===
    const evidence = path.join(os.tmpdir(), 'evidence.txt')
    await fs.writeFileSync(evidence, '自动化测试凭证内容')
    // 原生 <input type=file> 的 @change 可靠触发，setInputFiles 会派发 change 事件。
    await page.setInputFiles('.approval-action input[type=file]', evidence)
    await page.waitForTimeout(2000) // 等待上传凭证完成
    const uploadToasts = await page.locator('.el-message--success, .el-message--error').allInnerTexts()
    console.log('[upload] 上传后提示:', JSON.stringify(uploadToasts))
    const approveComment = '自动化测试：审批通过 ' + new Date().toISOString().slice(0, 19)
    await page.fill('textarea', approveComment)
    await page.click('button:has-text("通过审批")')
    await page.waitForTimeout(2000)
    const successMsg = await page.locator('.el-message--success').count()
    const errorMsg = await page.locator('.el-message--error').count()
    console.log('[approve] 提交后提示 success=' + successMsg + ' error=' + errorMsg + '（toast 仅作参考）')

    // 直验后端真实状态：审批历史中必须存在本次的 APPROVE 记录（唯一审批意见 + 凭证附件 id）。
    // 不用「待我审批列表行数减少」作判定：同一账号可连续审批多个节点，单据留在列表属正常行为。
    const approveRecord = await page.evaluate(async (comment) => {
      const headers = { Authorization: 'Bearer ' + localStorage.getItem('oa_token') }
      const listRes = await fetch('/api/documents/page?page=1&size=100', { headers })
      const listBody = await listRes.json()
      const docs = (listBody.data && listBody.data.content) || []
      for (const doc of docs) {
        const hRes = await fetch(`/api/documents/${doc.id}/actions/history`, { headers })
        const hBody = await hRes.json()
        const hit = (hBody.data || []).find(r =>
          r.source === 'BUSINESS' && r.action === 'APPROVE' &&
          r.comment === comment && r.evidenceAttachmentId != null)
        if (hit) return { docCode: doc.docCode, node: doc.currentNode, status: doc.status }
      }
      return null
    }, approveComment)
    log('审批通过操作', !!approveRecord,
      approveRecord ? `审批成功（后端已记录 APPROVE：${approveRecord.docCode}，当前节点=${approveRecord.node}，状态=${approveRecord.status}）`
                    : '未成功（后端审批历史中未找到本次 APPROVE 记录）')

    // === 6. 返回列表 ===
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)
    const drawerStillOpen = await page.locator('.el-overlay.is-drawer').count()
    if (drawerStillOpen > 0) {
      await page.locator('.el-drawer button:has-text("×")').click().catch(() => {})
      await page.waitForTimeout(500)
    }
    await page.click('text=待我审批')
    await page.waitForSelector('.el-table__row', { timeout: 5000 })
    await page.waitForTimeout(500)
    const filteredRows = await page.locator('.el-table__row').count()
    log('返回待我审批列表', filteredRows > 0,
      `${filteredRows} 行（同一账号可连续审批多个节点，单据不离开列表属正常）`)

    // === 6.5 发起单据（带附件）：新建日常付款 → 填表 → 传附件 → 提交 → 直验后端 ===
    let submitted = null
    try {
      // 流程配置按"项目名称"匹配（DocumentApplicationService.findByType(projectName)），
      // 手输新项目名必然 FLOW_CONFIG_NOT_FOUND，必须从既有项目列表选择。
      await page.click('text=工作台')
      await page.click('.zone.daily button:has-text("新建")')
      await page.waitForSelector('.el-dialog:visible', { timeout: 5000 })
      // 记录提交前的单据 id 集合，用于提交后定位新单
      const beforeIds = await page.evaluate(async () => {
        const headers = { Authorization: 'Bearer ' + localStorage.getItem('oa_token') }
        const list = await (await fetch('/api/documents/page?page=1&size=100', { headers })).json()
        return ((list.data && list.data.content) || []).map(d => d.id)
      })
      // 对应项目（第 3 个下拉）：选第一个既有项目（= 闭店，已有流程配置）
      await page.click('.el-dialog:visible .el-select >> nth=2')
      await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 5000 })
      await page.click('.el-select-dropdown:visible .el-select-dropdown__item >> nth=0')
      await page.fill('.el-dialog:visible .el-input-number input', '88.5')
      await page.locator('.el-dialog:visible textarea').nth(0).fill('自动化测试发起单据')
      await page.locator('.el-dialog:visible textarea').nth(1).fill('增值税专用发票1张，含税金额88.5元')
      for (let k = 0; k < 3; k++) {
        const f = path.join(os.tmpdir(), `submit-att-${k}.txt`)
        await fs.writeFileSync(f, `附件${k} 内容`)
        await page.locator('.attachment-grid input[type=file]').nth(k).setInputFiles(f)
      }
      await page.click('.el-dialog:visible button:has-text("提交审批")')
      await page.waitForTimeout(1500)
      const toasts = await page.locator('.el-message').allInnerTexts().catch(() => [])
      console.log('[submit] 提交后提示:', JSON.stringify(toasts))
      // 弹窗关闭 = 建单 + 3 个附件全部上传完成；未关闭则说明校验拦截/API 报错，Escape 收尾不阻塞后续步骤
      let closed = true
      try {
        await page.waitForSelector('.el-dialog:visible', { state: 'hidden', timeout: 12000 })
      } catch {
        closed = false
        await page.keyboard.press('Escape').catch(() => {})
        await page.waitForTimeout(500)
      }
      submitted = await page.evaluate(async ({ ids, closedFlag }) => {
        if (!closedFlag) return null
        const headers = { Authorization: 'Bearer ' + localStorage.getItem('oa_token') }
        const list = await (await fetch('/api/documents/page?page=1&size=100', { headers })).json()
        const doc = ((list.data && list.data.content) || []).find(d => !ids.includes(d.id))
        if (!doc) return null
        const detail = await (await fetch(`/api/documents/${doc.id}`, { headers })).json()
        return { docCode: doc.docCode, project: doc.projectName, node: doc.currentNode, attachments: ((detail.data && detail.data.attachments) || []).length }
      }, { ids: beforeIds, closedFlag: closed })
    } catch (e) {
      console.log('[submit] 发起单据异常:', e.message)
    }
    log('发起单据(带附件)', !!submitted && submitted.attachments >= 3,
      submitted ? `提交成功（${submitted.docCode}，节点=${submitted.node}，附件 ${submitted.attachments} 个）`
                : '未成功（后端未找到新单或附件缺失，详见 [submit] 日志）')

    // === 6.8 审批动作另一半：驳回 / 通过但补材料 / 加签（直验后端审批历史） ===
    await page.click('text=待我审批')
    await page.waitForSelector('.el-table__row', { timeout: 5000 })
    const verifyActionHistory = (code, text, action) => page.evaluate(async ({ code, text, action }) => {
      const headers = { Authorization: 'Bearer ' + localStorage.getItem('oa_token') }
      const list = await (await fetch('/api/documents/page?page=1&size=100', { headers })).json()
      const doc = ((list.data && list.data.content) || []).find(d => d.docCode === code)
      if (!doc) return null
      const h = await (await fetch(`/api/documents/${doc.id}/actions/history`, { headers })).json()
      const recs = h.data || []
      return recs.some(r => JSON.stringify(r).includes(text) || r.action === action)
        ? { node: doc.currentNode, status: doc.status } : null
    }, { code, text, action })

    const actionStamp = new Date().toISOString().slice(11, 19)

    async function runApprovalAction(stepName, buttonText, confirmText, fillDialog, action, text) {
      let code = null, res = null
      try {
        const row = page.locator('.el-table__row').first()
        code = ((await row.textContent()) || '').match(/[A-Z]{2}\d{10,}/)?.[0]
        await row.locator('.el-button:has-text("进入审批")').click()
        await page.waitForSelector('.el-drawer', { timeout: 5000 })
        await page.click(`.el-drawer button:has-text("${buttonText}")`)
        await page.waitForSelector('.el-dialog:visible', { timeout: 5000 })
        await fillDialog()
        await page.click(`.el-dialog:visible button:has-text("${confirmText}")`)
        await page.waitForTimeout(1500)
        console.log(`[${stepName}] 提示:`, JSON.stringify(await page.locator('.el-message').allInnerTexts().catch(() => [])))
        await page.keyboard.press('Escape').catch(() => {})
        await page.waitForTimeout(500)
        res = await verifyActionHistory(code, text, action)
      } catch (e) {
        console.log(`[${stepName}] 异常:`, e.message)
        await page.keyboard.press('Escape').catch(() => {})
        await page.locator('.el-dialog:visible button:has-text("取消")').click().catch(() => {})
        await page.keyboard.press('Escape').catch(() => {})
        await page.waitForTimeout(400)
      }
      log(stepName, !!res,
        res ? `成功（${code} → 节点=${res.node}，状态=${res.status}）`
            : `未成功（${code || '未识别单据'} 的审批历史中未找到 ${action} 记录）`)
    }

    await runApprovalAction('驳回审批', '驳回', '确认驳回', async () => {
      await page.fill('.el-dialog:visible textarea', '自动化测试驳回 ' + actionStamp)
    }, 'REJECT', '自动化测试驳回 ' + actionStamp)

    await runApprovalAction('通过但补材料', '通过但补材料', '确认通过并补充', async () => {
      await page.locator('.el-dialog:visible textarea').first().fill('自动化测试补材料 ' + actionStamp)
    }, 'SUPPLEMENT', '自动化测试补材料 ' + actionStamp)

    await runApprovalAction('发起加签', '加签', '发送加签', async () => {
      await page.click('.el-dialog:visible .el-select')
      await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 5000 })
      await page.click('.el-select-dropdown:visible .el-select-dropdown__item >> nth=1')
      await page.locator('.el-dialog:visible textarea').first().fill('自动化测试加签 ' + actionStamp)
    }, 'SIGN', '自动化测试加签 ' + actionStamp)

    // === 7. 台账档案 ===
    await page.click('text=台账档案')
    await page.waitForSelector('.el-table__row', { timeout: 5000 })
    const archiveRows = await page.locator('.el-table__row').count()
    log('台账档案', archiveRows > 0, `${archiveRows} 行`)

    // === 7.1 下载接口响应一致性断言 ===
    // 目的：防止有人把下载接口退化回「void + 直接写 HttpServletResponse」或包成 ApiResponse。
    // 正确形态必须是二进制文件流：Content-Type 非 JSON + 带 Content-Disposition: attachment + 有内容。
    // 一旦退化，响应会变成 application/json（导入浏览器只会显示 JSON 而非下载），此断言即失败。
    const exportProbe = await page.evaluate(async () => {
      const token = localStorage.getItem('oa_token')
      const res = await fetch('/api/archive-ledger/export', {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      })
      const disp = res.headers.get('content-disposition') || ''
      const ctype = res.headers.get('content-type') || ''
      const buf = new Uint8Array(await res.arrayBuffer())
      return { ok: res.ok, status: res.status, disp, ctype, size: buf.length }
    }).catch((e) => ({ ok: false, status: 0, disp: '', ctype: '', size: 0, error: String(e) }))
    const isBinaryDownload = exportProbe.ok
      && /attachment/.test(exportProbe.disp)
      && !/json/i.test(exportProbe.ctype)
      && exportProbe.size > 0
    log('台账导出返回二进制下载(非JSON包装)', isBinaryDownload,
      `status=${exportProbe.status} content-type=${exportProbe.ctype} disposition=${exportProbe.disp} size=${exportProbe.size}`)

    // === 8. 流程管理 ===
    await page.click('text=流程管理')
    await page.waitForSelector('.flow-config-grid .el-card', { timeout: 5000 })
    const flowRows = await page.locator('.flow-config-grid .el-card').count()
    log('流程管理', flowRows > 0, `${flowRows} 个流程`)

    // === 9. 权限管理 ===
    await page.click('text=权限管理')
    await page.waitForTimeout(2000)
    const empRows = await page.locator('.el-table__row').count()
    log('权限管理-员工', empRows > 0, `${empRows} 个员工`)

    // === 10. 角色 tab ===
    await page.locator('#tab-roles').click({ force: true })
    await page.waitForTimeout(1000)
    const roleTreeNodes = await page.locator('.role-tree-node').count()
    log('权限管理-角色', roleTreeNodes > 0, `${roleTreeNodes} 个节点`)

    // === 11. 控制台错误 ===
    log('控制台无错误', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))

    // === 12. 接口级断言（核心）===
    await page.waitForTimeout(1000)
    log('所有接口返回成功', apiFailures.length === 0,
      apiFailures.length ? `${apiFailures.length} 个失败：${apiFailures.slice(0, 5).join(' | ')}` : `共 ${apiCalls.length} 次调用`)

    // === 后端接口覆盖率：用 /v3/api-docs 对比实际调用 ===
    const docs = await page.request.get(`${API_ORIGIN}/v3/api-docs`).then(r => r.json()).catch(() => null)
    if (docs?.paths) {
      const templates = []
      for (const [path, methods] of Object.entries(docs.paths)) {
        for (const m of Object.keys(methods)) {
          if (['get', 'post', 'put', 'delete'].includes(m)) templates.push(`${m.toUpperCase()} ${path}`)
        }
      }
      const matches = (calledPath, tpl) => {
        const a = calledPath.split('/')
        const b = tpl.split('/')
        return a.length === b.length && b.every((seg, i) => seg.startsWith('{') || seg === a[i])
      }
      const covered = new Set()
      for (const call of apiCalls) {
        for (const tpl of templates) {
          const [m, p] = tpl.split(' ')
          if (m === call.method && matches(call.path, p)) covered.add(tpl)
        }
      }
      const uncovered = templates.filter(t => !covered.has(t)).sort()
      console.log(`\n--- 后端接口覆盖：${covered.size}/${templates.length}，未覆盖 ${uncovered.length} 个 ---`)
      uncovered.forEach(t => console.log('  · ' + t))
    }
  } catch (err) {
    log('测试异常', false, err.message)
  } finally {
    await browser.close()
  }

  const passed = results.filter(r => r.ok).length
  const total = results.length
  console.log(`\n=== 结果: ${passed}/${total} 通过 ===`)
  if (passed < total) {
    console.log('失败项:')
    results.filter(r => !r.ok).forEach(r => console.log(`  ❌ ${r.name}: ${r.detail}`))
  }
  if (apiFailures.length) {
    console.log('\n接口失败明细:')
    apiFailures.forEach(f => console.log('  ✗ ' + f))
  }
  process.exit(passed === total ? 0 : 1)
}

main()
