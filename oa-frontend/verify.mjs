import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('pageerror', e => errors.push(e.message));
await page.goto('http://localhost:5173/design-app.html', { waitUntil: 'networkidle' });
await page.waitForTimeout(2000);
const hasToken = await page.evaluate(() => !!localStorage.getItem('oa_token'));
if (!hasToken) {
  const r = await page.request.post('http://localhost:8080/api/auth/login', { data: { account: 'linanran', password: 'password' }, headers: { 'Content-Type': 'application/json' } });
  const j = await r.json();
  console.log('login resp:', j.success, j.message || '');
  if (j.success && j.data?.accessToken) { await page.evaluate(t => localStorage.setItem('oa_token', t), j.data.accessToken); await page.reload({ waitUntil: 'networkidle' }); await page.waitForTimeout(2000); }
}
const info = await page.evaluate(() => {
  const chain = el => { const a = []; let e = el; while (e && e !== document.body) { a.push(e.tagName + '.' + (e.className || '').toString().split(' ')[0]); e = e.parentElement; } return a.join(' < '); };
  const r = el => { const b = el.getBoundingClientRect(); return `x=${Math.round(b.x)} w=${Math.round(b.width)}`; };
  const dlg = document.querySelector('#app > el-dialog, #app .el-dialog');
  return {
    appDisplay: getComputedStyle(document.querySelector('#app')).display,
    loginVisible: !!document.querySelector('#login-app') && getComputedStyle(document.querySelector('#login-app')).display !== 'none',
    mainChain: document.querySelector('.el-main') ? chain(document.querySelector('.el-main')) : '(no .el-main)',
    mainRect: document.querySelector('.el-main') ? r(document.querySelector('.el-main')) : 'n/a',
    dialogInApp: !!document.querySelector('#app el-dialog'),
    drawerInApp: !!document.querySelector('#app el-drawer')
  };
});
console.log(JSON.stringify(info, null, 2));
console.log('pageerrors:', errors.length ? errors : '(无)');
await browser.close();
