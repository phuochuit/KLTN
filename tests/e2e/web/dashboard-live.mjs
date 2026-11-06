// Explicit opt-in owner demo: read-only UI/API checks. Never log credential, cookie or row identifiers.
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const run = path.resolve(process.argv[2] ?? '');
assert.ok(run.startsWith(path.join(root, '.artifacts/test-runs') + path.sep));
const stage = process.argv[3] ?? 'baseline';
assert.match(stage, /^[a-z0-9-]+$/);
const output = path.join(run, stage); await mkdir(output, { recursive: false });
const credential = JSON.parse(await readFile(path.join(root, 'scripts/security/keys/dashboard-demo-login.local.json'), 'utf8'));
assert.equal(credential.origin, 'http://localhost:8082', 'Confirm a new origin before reusing this demo credential');
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const report = { mode: 'Connected owner-approved demo, no API mocking/no DB seeding', observations: [] };
report.checks=[];
let currentCheck='login and initial data';
const check=async(name,fn)=>{currentCheck=name;await fn();report.checks.push({check:name,status:'PASS'});};
try {
  const page = await browser.newPage({ baseURL: credential.origin, viewport: { width: 1440, height: 900 } });
  await page.goto('/login');
  await page.locator('#username').fill(credential.username); await page.locator('#password').fill(credential.password);
  await page.locator('#loginSubmit').click(); await expect(page).toHaveURL(url=>url.origin===credential.origin&&url.pathname==='/');
  if (stage !== 'baseline') {
    const readiness=await page.request.get('/api/dashboard/overview');
    if (!readiness.ok()) { const error=new Error('New read APIs unavailable; owner Web restart required'); error.name='DeploymentPending'; throw error; }
  }
  await expect(page.locator('#compositionPeriod')).toContainText('Tháng');
  const monthly = await page.request.get(stage==='baseline'?'/api/parking/revenue/monthly':'/api/parking/revenue/trend'); assert.ok(monthly.ok());
  const series = await monthly.json();
  const current = series.at(-1);
  const response = await page.request.get(`/api/parking/revenue/filter?type=month&year=${current.year}&month=${current.month}`);
  assert.ok(response.ok()); const data = await response.json();
  assert.equal(Number(current.totalRevenue), Number(data.totalRevenue));
  assert.equal(data.sessions.reduce((sum, row) => sum + Number(row.fee), 0) + data.payments.reduce((sum, row) => sum + Number(row.amount), 0), Number(data.totalRevenue));
  report.currentPeriod = { year: current.year, month: current.month, visitRevenue: data.visitRevenue, subscriptionRevenue: data.subscriptionRevenue, totalRevenue: data.totalRevenue, transactionCount: data.sessions.length + data.payments.length };
  if (stage !== 'baseline') {
    const period=`type=month&year=${current.year}&month=${current.month}`;
    const summaryResponse=await page.request.get('/api/parking/revenue/summary?'+period);assert.ok(summaryResponse.ok());const summary=await summaryResponse.json();
    assert.equal(Number(summary.totalRevenue),Number(current.totalRevenue));
    let totalItems=0,sum=0;const identifiers=new Set();const sourceCounts={VISIT:0,SUBSCRIPTION:0};
    for(let p=0;;p++){
      const response=await page.request.get(`/api/parking/revenue/transactions?${period}&page=${p}&size=10`);assert.ok(response.ok());const result=await response.json();
      totalItems=result.totalItems;for(const item of result.items){const key=item.source+':'+item.id;assert.ok(!identifiers.has(key));identifiers.add(key);sum+=Number(item.amount);sourceCounts[item.source]++;}
      if(p+1>=result.totalPages)break;
    }
    assert.equal(sum,Number(summary.totalRevenue));assert.equal(identifiers.size,totalItems);
    report.newReadApi={status:'PASS',totalItems,totalRevenue:summary.totalRevenue,duplicates:0};
    report.checks.push({check:'real trend/summary/all transaction pages agree with no duplicate DTOs',status:'PASS'});
    await expect(page.locator('#snapshotTime')).toContainText('Cập nhật:');
    await expect(page.locator('#pageSize')).toHaveCount(0);await expect(page.locator('#pageRange')).toContainText(`/ ${totalItems} giao dịch`);
    if(totalItems>10){await page.locator('#nextPage').click();await expect(page.locator('#pageRange')).toContainText('Hiển thị 11');}
    await check('real search/source filters keep global period total unchanged',async()=>{
      const before=await page.locator('#periodTotal').textContent();
      await page.locator('#transactionSearch').fill('__dashboard_ux_no_match__');await page.locator('#transactionFilter button').click();
      await expect(page.locator('#transactionStatus')).toContainText('Không tìm thấy');await expect(page.locator('#nextPage')).toBeDisabled();await expect(page.locator('#clearTransactionFilters')).toBeVisible();
      assert.equal(await page.locator('#periodTotal').textContent(),before);
      await page.locator('#transactionSearch').fill('');
      for(const source of ['VISIT','SUBSCRIPTION','ALL']){
        await page.locator('#transactionSource').selectOption(source);await page.locator('#transactionFilter button').click();
        await expect(page.locator('#pageRange')).toContainText(`/ ${source==='ALL'?totalItems:sourceCounts[source]} giao dịch`);
        assert.equal(await page.locator('#periodTotal').textContent(),before);
      }
    });
    await check('connected URL reload, Back/Forward and server order',async()=>{
      await page.locator('#transactionSort').selectOption('oldest');await page.locator('#transactionFilter button').click();
      await expect(page.locator('#pageRange')).toContainText(`/ ${totalItems} giao dịch`);const original=page.url();
      await page.reload();await expect(page.locator('#transactionSort')).toHaveValue('oldest');await expect(page.locator('#pageRange')).toContainText(`/ ${totalItems} giao dịch`);
      await page.locator('#transactionSource').selectOption('VISIT');await page.locator('#transactionFilter button').click();await expect(page.locator('#pageRange')).toContainText(`/ ${sourceCounts.VISIT} giao dịch`);
      await page.goBack();await expect(page).toHaveURL(original);await expect(page.locator('#pageRange')).toContainText(`/ ${totalItems} giao dịch`);
      await page.goForward();await expect(page.locator('#pageRange')).toContainText(`/ ${sourceCounts.VISIT} giao dịch`);
      const orderedResponse=await page.request.get(`/api/parking/revenue/transactions?${period}&size=20&sort=oldest`);assert.ok(orderedResponse.ok());const ordered=await orderedResponse.json();
      for(let i=1;i<ordered.items.length;i++){const a=ordered.items[i-1],b=ordered.items[i];assert.ok(a.occurredAt<b.occurredAt||(a.occurredAt===b.occurredAt&&(a.source<b.source||(a.source===b.source&&a.id<=b.id))));}
    });
    await check('real period filters validate dates and month drilldown preserves snapshot KPIs',async()=>{
      const snapshot=await page.locator('#currentToday').textContent();
      await page.locator('#filterType').selectOption('year');await page.locator('#revenueFilter button').click();await expect(page.locator('#compositionPeriod')).toHaveText(`Năm ${current.year}`);
      const yearly=await (await page.request.get(`/api/parking/revenue/summary?type=year&year=${current.year}`)).json();
      await expect(page.locator('#periodTotal')).toHaveText(Number(yearly.totalRevenue).toLocaleString('vi-VN')+' đ');
      await page.locator('#filterType').selectOption('range');await page.locator('#filterFrom').fill(`${current.year}-02-02`);await page.locator('#filterTo').fill(`${current.year}-01-01`);await page.locator('#revenueFilter button').click();await expect(page.locator('#filterStatus')).toContainText('ngày bắt đầu không sau');
      await page.locator('#resetPeriod').click();await expect(page.locator('#compositionPeriod')).toHaveText(`Tháng ${current.month}/${current.year}`);
      await page.locator('.month-bar').last().click();await expect(page.locator('#compositionPeriod')).toHaveText(`Tháng ${current.month}/${current.year}`);
      assert.equal(await page.locator('#currentToday').textContent(),snapshot);
      await page.locator('#viewPeriodTransactions').click();await expect(page.locator('#transactionsTitle')).toBeFocused();
    });
    await check('real overview refresh and permission-respecting KPI OPEN destination',async()=>{
      await page.locator('#refreshOverview').click();await expect(page.locator('#snapshotTime')).toContainText('Cập nhật:');
      const overview=await (await page.request.get('/api/dashboard/overview')).json();
      assert.equal(Number((await page.locator('#currentVehicles').textContent()).replace(/\D/g,'')),overview.vehicleCount);
      const destination=await page.request.get('/sessions?status=OPEN');assert.ok(destination.ok());
      assert.ok((await destination.text()).includes('Xem tất cả trạng thái'));
    });
  }
  const masks = [page.locator('.account-link'), page.locator('.sessions-panel tbody'), page.locator('#detailBody')];
  for (const [width,height] of [[1440,900],[1280,800],[1024,768],[768,1024],[430,932],[375,812],[320,640]]) {
    await page.setViewportSize({ width,height }); await page.evaluate(() => document.fonts.ready);
    await page.evaluate(()=>{document.activeElement?.blur();scrollTo(0,0);});
    await page.screenshot({ path: path.join(output, `viewport-${width}.png`), fullPage: true, mask: masks,maskColor:'#E4E9F3' });
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`Body overflow ${width}`);
    report.observations.push(await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth, sidebar: (() => { const node = document.querySelector('.sidebar'); return { height: node.clientHeight, scrollHeight: node.scrollHeight }; })(), customizableSelect: CSS.supports('appearance','base-select') })));
  }
  if(stage!=='baseline'){
    await check('connected sidebar short height and real opened native dropdowns',async()=>{
      await page.setViewportSize({width:1280,height:640});await page.evaluate(()=>scrollTo(0,0));
      await page.locator('.sidebar .nav-extra > summary').click();
      await page.locator('.sidebar .nav-extra a').last().scrollIntoViewIfNeeded();
      const metrics=await page.evaluate(()=>{const s=document.querySelector('.sidebar'),n=s.querySelector('nav'),f=s.querySelector('.navigation-bottom');return{outer:s.scrollHeight-s.clientHeight,scrollable:n.scrollHeight>n.clientHeight,footer:f.getBoundingClientRect().bottom,viewport:innerHeight};});
      assert.ok(metrics.outer<=2&&metrics.scrollable&&metrics.footer<=metrics.viewport);
      await page.screenshot({path:path.join(output,'sidebar-short.png'),mask:masks,maskColor:'#E4E9F3'});
      await page.locator('#filterType').scrollIntoViewIfNeeded();await page.locator('#filterType').click();
      await page.screenshot({path:path.join(output,'dropdown-open.png'),mask:masks,maskColor:'#E4E9F3'});await page.keyboard.press('Escape');await expect(page.locator('#filterType')).toBeFocused();
      await page.setViewportSize({width:375,height:640});await page.locator('#transactionSource').scrollIntoViewIfNeeded();await page.locator('#transactionSource').click();
      await page.screenshot({path:path.join(output,'dropdown-mobile-bottom.png'),mask:masks,maskColor:'#E4E9F3'});await page.keyboard.press('Escape');
      await page.goto('/');await expect(page.locator('.month-bar')).toHaveCount(6);await expect(page.locator('#pageSize')).toHaveCount(0);await expect(page.locator('#pageRange')).toContainText(`/ ${report.newReadApi.totalItems} giao dịch`);
      const chart=page.locator('#revenueChart');await chart.evaluate(node=>{node.scrollLeft=node.scrollWidth;});
      const chartBox=await chart.boundingBox(),axisBox=await page.locator('.chart-axis').boundingBox();
      assert.ok(axisBox.x>=chartBox.x-1&&axisBox.x+axisBox.width<=chartBox.x+chartBox.width+1,'Money axis must stay readable while chart scrolls');
      await page.evaluate(()=>{document.activeElement?.blur();scrollTo(0,0);});
      await page.screenshot({path:path.join(output,'mobile-entry-375.png'),fullPage:true,mask:masks,maskColor:'#E4E9F3'});
    });
  }
  report.status = 'PASS';
} catch (error) { report.status = error.name==='DeploymentPending'?'BLOCKED':'FAIL'; report.errorType = error.name;report.failedCheck=currentCheck; process.exitCode = error.name==='DeploymentPending'?2:1; }
finally { await writeFile(path.join(output, 'summary.json'), JSON.stringify(report, null, 2)); console.log(JSON.stringify(report, null, 2)); await browser.close(); }
