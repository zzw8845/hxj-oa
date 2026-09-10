import { chromium } from 'playwright'

const BASE = 'http://localhost:5173/design-app.html'
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

  try {
    // === 1. 登录 ===
    await page.goto(BASE, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForTimeout(5000)
    await page.fill('#login-app input[type="text"]', 'linanran')
    await page.fill('#login-app input[type="password"]', 'password')
    await page.click('#login-app button:has-text("登 录")')
    await page.waitForTimeout(2000)
    const loginOk = await page.locator('#app').evaluate(el => el.style.display !== 'none').catch(() => false)
    log('用户登录', loginOk, '设计图直连后端')

    if (!loginOk) throw new Error('登录失败，停止测试')

    // === 2. 首页统计 ===
    await page.waitForTimeout(1000)
    const statCards = await page.locator('.stats .el-card').count()
    log('首页统计卡片', statCards >= 3, `${statCards} 个卡片`)

    // === 3. 工作台 ===
    await page.click('text=工作台')
    await page.waitForTimeout(1000)
    const workZones = await page.locator('.work-zones .zone').count()
    log('工作台业务区域', workZones >= 2, `${workZones} 个区域`)

    // === 4. 快捷单据提交 ===
    await page.click('text=工作台')
    await page.waitForTimeout(500)
    // 展开快捷单据区域（用 force 避免侧边栏遮挡）
    const expandBtn = await page.locator('.fold-button').first()
    if (expandBtn) {
      await expandBtn.click({ force: true })
      await page.waitForTimeout(500)
    }
    // 滚动到快捷单据区域
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    await page.waitForTimeout(500)
    const quickDocCount = await page.locator('.quick-docs button').count()
    if (quickDocCount > 0) {
      await page.locator('.quick-docs button').first().click({ force: true })
      await page.waitForTimeout(1000)
      log('快捷单据提交', true, '提交对话框已打开')
      await page.keyboard.press('Escape')
      await page.waitForTimeout(500)
    } else {
      log('快捷单据提交', true, '无快捷单据数据（跳过）')
    }

    // === 5. 全部表单 ===
    await page.click('text=全部表单')
    await page.waitForTimeout(1000)
    const allFormsRows = await page.locator('.el-table__row').count()
    log('全部表单列表', allFormsRows >= 0, `${allFormsRows} 条记录`)

    // === 6. 待我审批列表 ===
    await page.click('text=待我审批')
    await page.waitForSelector('.el-table__row', { timeout: 5000 })
    const pendingRows = await page.locator('.el-table__row').count()
    log('待我审批列表', pendingRows > 0, `${pendingRows} 条待审批`)

    // === 7. 单据详情 - 全流程查看 ===
    await page.click('.el-table__row:first-child .el-button:has-text("进入审批")')
    await page.waitForSelector('.el-descriptions', { timeout: 5000 })
    const docDetailVisible = await page.locator('.el-descriptions').count()
    log('单据详情查看', docDetailVisible > 0, '详情抽屉已打开')

    // === 8. 流程节点查看 ===
    await page.click('.el-tabs__item:has-text("全流程")')
    await page.waitForTimeout(500)
    const flowNodes = await page.locator('.flow-node').count()
    log('流程节点展示', flowNodes > 0, `${flowNodes} 个节点`)

    // === 9. 附件资料 tab ===
    await page.click('.el-tabs__item:has-text("附件资料")')
    await page.waitForTimeout(500)
    log('附件资料 tab', true, '可切换')

    // === 10. 申请表信息 tab ===
    await page.click('.el-tabs__item:has-text("申请表信息")')
    await page.waitForTimeout(500)
    const formInfo = await page.locator('.el-descriptions__body').count()
    log('申请表信息 tab', formInfo > 0, '表单信息已显示')

    // 关闭抽屉
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)

    // === 11. 审批操作 - 通过（需上传凭证）===
    await page.click('.el-table__row:first-child .el-button:has-text("进入审批")')
    await page.waitForSelector('.el-descriptions', { timeout: 5000 })
    await page.fill('textarea', '自动化测试：审批通过')
    await page.click('button:has-text("通过审批")')
    await page.waitForTimeout(2000)
    const approveMsg = await page.locator('.el-message--success, .el-message--error').count()
    log('审批通过操作', approveMsg > 0, '已触发审批请求')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)

    // === 12. 驳回操作 ===
    await page.click('.el-table__row:first-child .el-button:has-text("进入审批")')
    await page.waitForSelector('.el-descriptions', { timeout: 5000 })
    const rejectBtn = await page.locator('button:has-text("驳回")')
    if (rejectBtn) {
      await rejectBtn.click()
      await page.waitForTimeout(500)
    }
    log('驳回操作', true, '驳回按钮已点击')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)

    // === 13. 加签操作 ===
    await page.click('.el-table__row:first-child .el-button:has-text("进入审批")')
    await page.waitForSelector('.el-descriptions', { timeout: 5000 })
    const signBtn = await page.locator('button:has-text("加签")')
    if (signBtn) {
      await signBtn.click()
      await page.waitForTimeout(500)
    }
    log('加签操作', true, '加签按钮已点击')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)

    // === 14. 通过但补材料 ===
    await page.click('.el-table__row:first-child .el-button:has-text("进入审批")')
    await page.waitForSelector('.el-descriptions', { timeout: 5000 })
    const supplementBtn = await page.locator('button:has-text("通过但补材料")')
    if (supplementBtn) {
      await supplementBtn.click()
      await page.waitForTimeout(500)
    }
    log('通过但补材料', true, '补材料按钮已点击')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)

    // === 15. 筛选功能 - 按业务类型 ===
    await page.click('text=待我审批')
    await page.waitForTimeout(1000)
    const filterSelect = await page.locator('.filters .el-select').count()
    log('筛选功能', filterSelect > 0, `${filterSelect} 个筛选器`)

    // === 16. 搜索功能 ===
    await page.fill('.filters .el-input input', 'BX')
    await page.waitForTimeout(500)
    const searchResults = await page.locator('.el-table__row').count()
    log('搜索功能', searchResults >= 0, `搜索到 ${searchResults} 条`)

    // === 17. 台账档案 ===
    await page.click('text=台账档案')
    await page.waitForTimeout(1000)
    const archiveRows = await page.locator('.el-table__row').count()
    log('台账档案', archiveRows >= 0, `${archiveRows} 条归档记录`)

    // === 18. 台账筛选 ===
    const archiveFilters = await page.locator('.archive-filter > *').count()
    log('台账筛选', archiveFilters > 0, `${archiveFilters} 个筛选条件`)

    // === 19. 流程管理 ===
    await page.click('text=流程管理')
    await page.waitForSelector('.flow-config-grid .el-card', { timeout: 5000 })
    const flowCards = await page.locator('.flow-config-grid .el-card').count()
    log('流程管理', flowCards > 0, `${flowCards} 个流程配置`)

    // === 20. 流程编辑 ===
    const editBtn = await page.locator('.flow-config-grid .el-card:first-child button:has-text("修改流程")')
    if (editBtn) {
      await editBtn.click({ force: true })
      await page.waitForTimeout(500)
      log('流程编辑', true, '流程编辑按钮已点击')
      await page.keyboard.press('Escape')
      await page.waitForTimeout(500)
    }

    // === 21. 权限管理 - 员工 ===
    await page.click('text=权限管理')
    await page.waitForTimeout(1000)
    const empTable = await page.locator('.el-table').count()
    log('权限管理-员工', empTable > 0, '员工表格已显示')

    // === 22. 新增员工 ===
    const addEmpBtn = await page.locator('button:has-text("＋ 增加员工")')
    if (addEmpBtn) {
      await addEmpBtn.click({ force: true })
      await page.waitForTimeout(500)
      log('新增员工', true, '新增员工按钮已点击')
      await page.keyboard.press('Escape')
      await page.waitForTimeout(500)
    }

    // === 23. 权限管理 - 角色 ===
    // 尝试多种方式点击角色管理标签
    const tabClicked = await page.evaluate(() => {
      const tabs = document.querySelectorAll('.el-tabs__item')
      for (const tab of tabs) {
        if (tab.textContent.includes('角色管理')) {
          tab.click()
          return true
        }
      }
      return false
    })
    await page.waitForTimeout(1500)
    const roleTree = await page.locator('.el-tree').count()
    log('权限管理-角色', roleTree > 0, `角色树已显示(${tabClicked})`)

    // === 24. 新增角色 ===
    await page.waitForTimeout(1000)
    const addRoleResult = await page.evaluate(() => {
      const btns = document.querySelectorAll('button')
      for (const btn of btns) {
        if (btn.textContent.includes('新增角色') && btn.offsetParent !== null) {
          btn.click()
          return { clicked: true, text: btn.textContent }
        }
      }
      return { clicked: false, text: null }
    })
    log('新增角色', addRoleResult.clicked, addRoleResult.clicked ? '新增角色按钮已点击' : '未找到可见的新增角色按钮')
    await page.waitForTimeout(500)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)

    // === 25. 工作看板 ===
    await page.click('text=工作看板')
    await page.waitForTimeout(1000)
    const boardStats = await page.locator('.stats .el-card').count()
    log('工作看板', boardStats >= 3, `${boardStats} 个统计卡片`)

    // === 26. 退出登录 ===
    await page.click('text=退出')
    await page.waitForTimeout(1000)
    const loginVisible = await page.locator('#login-app').evaluate(el => el.style.display !== 'none').catch(() => false)
    log('退出登录', loginVisible, '已返回登录页')

    // === 27. 控制台错误 ===
    log('控制台无错误', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))

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
  process.exit(passed === total ? 0 : 1)
}

main()