import { chromium } from 'playwright'
const b = await chromium.launch()
const p = await (await b.newContext()).newPage()
await p.goto('http://localhost:5173/' + encodeURIComponent('海峡金OA审批系统原型.html'))
await p.waitForSelector('#login-app input[type="text"]', { timeout: 5000 })
await p.fill('#login-app input[type="text"]', 'linanran')
await p.fill('#login-app input[type="password"]', 'password')
await p.click('#login-app button:has-text("登 录")')
await p.waitForTimeout(4000)
const result = await p.evaluate(async () => {
  const headers = { Authorization: 'Bearer ' + localStorage.getItem('oa_token'), 'Content-Type': 'application/json' }
  const list = await (await fetch('/api/flow-configs', { headers })).json()
  const out = []
  for (const brief of list.data) {
    const detail = await (await fetch(`/api/flow-configs/${brief.id}`, { headers })).json()
    const c = detail.data
    const payload = {
      type: c.type, category: c.category,
      nodes: c.nodes.map(n => ({ name: n.name, nodeType: n.nodeType, assigneeRole: n.assigneeRole })),
      conditionRules: c.conditionRules.map(r => ({
        variableName: r.variableName, operator: r.operator,
        expectedValue: r.expectedValue, targetNodeName: r.targetNodeName
      }))
    }
    const res = await (await fetch(`/api/admin/flow-configs/${c.id}`, {
      method: 'PUT', headers, body: JSON.stringify(payload)
    })).json()
    out.push(`${c.type}: ${res.success ? '重部署OK' : res.code + ' ' + res.message}`)
  }
  return out
})
console.log(result.join('\n'))
await b.close()
