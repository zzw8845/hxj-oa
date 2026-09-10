import { chromium } from 'playwright'
const b = await chromium.launch()
const p = await (await b.newContext()).newPage()
await p.goto('http://localhost:5173/' + encodeURIComponent('海峡金OA审批系统原型.html'))
await p.waitForSelector('#login-app input[type="text"]', { timeout: 5000 })
await p.fill('#login-app input[type="text"]', 'wangyang')
await p.fill('#login-app input[type="password"]', 'password')
await p.click('#login-app button:has-text("登 录")')
await p.waitForTimeout(4000)
console.log('汪洋待我审批:', await p.evaluate(async () => {
  const headers = { Authorization: 'Bearer ' + localStorage.getItem('oa_token') }
  const list = await (await fetch('/api/documents/page?page=1&size=100&myPending=true', { headers })).json()
  return JSON.stringify((list.data.content || []).map(d => d.docCode + '@' + d.currentNode))
}))
await b.close()
