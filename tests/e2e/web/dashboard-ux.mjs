// Real isolated Spring APIs first; explicit browser-only synthetic data for larger UX states afterwards.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir, readdir, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=fileURLToPath(new URL('../../../',import.meta.url));
const require=createRequire(path.join(root,'spring-web/package.json'));
const {chromium,expect}=require('@playwright/test');
const runArg=process.argv[2]??''; const runs=await (await import('node:fs/promises')).realpath(path.join(root,'.artifacts/test-runs'));
const run=await (await import('node:fs/promises')).realpath(path.resolve(runArg));
assert.ok(run.startsWith(runs+path.sep),'Run path must resolve below .artifacts/test-runs');
const stage=process.argv[3]??'final'; assert.match(stage,/^(?:preview|final(?:-[a-z0-9]+)*)$/);
const finalStage=stage==='final'||stage.startsWith('final-');
const output=path.join(run,stage);
const portText=(await readFile(path.join(run,'server.port'),'utf8')).trim(); assert.match(portText,/^\d{1,5}$/);
const port=Number(portText); assert.ok(port>0&&port<=65535); const baseURL=`http://127.0.0.1:${port}`;
const stopPath=path.join(run,'stop');
try { await readFile(stopPath); throw new Error('Harness stop marker exists.'); } catch(error) { if(error.code!=='ENOENT') throw error; }
const pidText=(await readFile(path.join(run,'controller.pid'),'utf8')).trim(); assert.match(pidText,/^\d+$/); const controllerPid=Number(pidText);
let listenerProbe;
try { listenerProbe=execFileSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',
  `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; if (!$controller) { exit 2 }; $listener = Get-NetTCPConnection -State Listen -LocalPort ${port} -ErrorAction SilentlyContinue; if (!$listener) { $client = New-Object System.Net.Sockets.TcpClient; $connected = $false; try { $pending = $client.BeginConnect('127.0.0.1', ${port}, $null, $null); try { if ($pending.AsyncWaitHandle.WaitOne(1500)) { $client.EndConnect($pending); $connected = $client.Connected } } finally { $pending.AsyncWaitHandle.Close() } } finally { $client.Close() }; if (!$connected) { exit 3 }; Write-Output 'loopback-connect' } else { Write-Output 'listener-table' }`],{encoding:'utf8',timeout:5000}).trim(); }
catch { throw new Error('Harness PID or actual loopback listener is absent; helper will not restart it.'); }
await mkdir(output,{recursive:false});
const tempDirectory=path.join(root,'.cache/test-temp',`${path.basename(run)}-${stage}`); await mkdir(tempDirectory,{recursive:false});
const savedTemp=process.env.TEMP,savedTmp=process.env.TMP; process.env.TEMP=tempDirectory;process.env.TMP=tempDirectory;
const fixture=await readFile(path.join(root,'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'),'utf8');
const executablePath='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const manifestFiles=['tests/e2e/web/dashboard-ux.mjs','spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
  'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'];
const manifestFolders=['spring-web/src/main/java','spring-web/src/main/resources/static','spring-web/src/main/resources/templates','spring-web/src/main/frontend',
  'spring-web/target/classes'];
async function walk(folder,files){for(const entry of await readdir(folder,{withFileTypes:true})){const full=path.join(folder,entry.name);if(entry.isDirectory())await walk(full,files);else if(entry.isFile())files.push(full);}}
async function sourceIdentity(){const files=manifestFiles.map(name=>path.join(root,name));for(const folder of manifestFolders){try{if((await stat(path.join(root,folder))).isDirectory())await walk(path.join(root,folder),files);}catch(error){if(error.code!=='ENOENT')throw error;}}const manifest=[];for(const file of [...new Set(files)].sort()){const bytes=await readFile(file);manifest.push({path:path.relative(root,file).replaceAll(path.sep,'/'),sha256:createHash('sha256').update(bytes).digest('hex')});}return manifest;}
const sourceBefore=await sourceIdentity();
let browser;
const checks=[];
let activeStep={name:'startup',location:'helper setup',category:'setup'};
const apiStatuses=[];
const guards={loginPostsForwarded:0,logoutPostsForwarded:0,businessPostsBlocked:0,otherMutationsBlocked:0,externalRequestsBlocked:0};
let realTransactionMetadata;
let realSummaryLabel;
const atStep=async(name,location,action,category='playwright-await')=>{
  activeStep={name,location,category};
  checks.push({status:'STEP STARTED',...activeStep});
  const value=await action();
  checks.push({status:'STEP COMPLETED',...activeStep});
  return value;
};
const check=async(name,fn)=>{activeStep={name,location:'check body',category:'assertion'};checks.push({check:name,status:'STARTED'});await fn();checks.push({check:name,status:'PASS'});};
const watchdog=setTimeout(()=>{
  checks.push({status:'FAIL',step:activeStep.name,category:'overall-timeout',location:activeStep.location});
  process.exitCode=1;
  void writeFile(path.join(output,'checks.json'),JSON.stringify(checks,null,2)).catch(()=>{});
  void browser?.close().catch(()=>{});
},90000);
try {
  browser=await atStep('launch browser','Edge launch',()=>chromium.launch({executablePath,headless:true}));
  const page=await atStep('create page','browser context',()=>browser.newPage({baseURL,viewport:{width:1440,height:1000}}));
  await atStep('install request mutation guard','browser context route',()=>page.context().route('**/*',async route=>{const request=route.request();let target;try{target=new URL(request.url());}catch{guards.externalRequestsBlocked++;return route.abort();}
    if(target.origin!==baseURL){guards.externalRequestsBlocked++;return route.abort();}const method=request.method().toUpperCase();
    if(method==='GET'||method==='HEAD')return route.continue();
    if(method==='POST'&&target.pathname==='/login'){guards.loginPostsForwarded++;return route.continue();}
    if(method==='POST'&&target.pathname==='/logout'){guards.logoutPostsForwarded++;return route.continue();}
    if(method==='POST'&&/^\/(pricing|payments)(\/|$)/.test(target.pathname))guards.businessPostsBlocked++;else guards.otherMutationsBlocked++;
    return route.abort();}));
  const cdp=await atStep('create read-only CDP session','Page domain',()=>page.context().newCDPSession(page)); await atStep('enable layout metrics','CDP Page.enable',()=>cdp.send('Page.enable'));
  const layout=async()=>cdp.send('Page.getLayoutMetrics');
  const pageY=async()=>{const m=await layout();return m.cssVisualViewport?.pageY ?? m.cssLayoutViewport.pageY;};
  const noHorizontalOverflow=async()=>{const m=await layout();return m.cssContentSize.width<=m.cssLayoutViewport.clientWidth+1;};
const errors=[]; page.on('pageerror',e=>errors.push(e.name));
await atStep('load login page','GET /login',()=>page.goto('/login'));
  await atStep('fill synthetic username','locator #username',()=>page.locator('#username').fill('lumipark-browser-manager'));
  await atStep('fill synthetic password','locator #password',()=>page.locator('#password').fill(fixture.match(/PASSWORD = "([^"]+)"/)[1]));
await atStep('submit synthetic login','locator #loginSubmit',()=>page.locator('#loginSubmit').click());
await atStep('wait for dashboard after login','URL /',()=>expect(page).toHaveURL(url=>url.origin===baseURL&&url.pathname==='/',{timeout:5000}));
const now=new Date(), year=now.getFullYear(), month=now.getMonth()+1;
await check('real isolated overview/summary/transaction API and seeded dashboard state',async()=>{
  await atStep('assert snapshot timestamp','locator #snapshotTime',()=>expect(page.locator('#snapshotTime')).toContainText('Cập nhật:'));
  const transactionsPath=`/api/parking/revenue/transactions?type=month&year=${year}&month=${month}&page=0&size=10`;
  let transactionsResponse, summaryResponse;
  for(const endpoint of ['/api/dashboard/overview','/api/parking/revenue/trend',`/api/parking/revenue/summary?type=month&year=${year}&month=${month}`,transactionsPath]) {
    const pathname=new URL(endpoint,baseURL).pathname;
    const response=await atStep(`GET ${pathname}`,pathname,()=>page.request.get(endpoint));
    apiStatuses.push({path:pathname,status:response.status()});
    activeStep={name:`assert GET ${pathname} is successful`,location:pathname,category:'http-status'};
    assert.ok(response.ok(),`HTTP status assertion failed for ${pathname}`);
    if(pathname.endsWith('/transactions')) transactionsResponse=response;
    if(pathname.endsWith('/summary')) summaryResponse=response;
  }
  const summary=await atStep('read real summary label',`/api/parking/revenue/summary?type=month&year=${year}&month=${month}`,()=>summaryResponse.json(),'api-metadata');
  assert.equal(typeof summary.label,'string'); assert.ok(summary.label);
  realSummaryLabel=summary.label;
  await atStep('assert dashboard period matches real summary API','#compositionPeriod',()=>expect(page.locator('#compositionPeriod')).toHaveText(realSummaryLabel));
  const transactionPage=await atStep('read real transaction pagination metadata',transactionsPath,()=>transactionsResponse.json(),'api-metadata');
  const {page:apiPage,size:apiSize,totalItems,totalPages,items}=transactionPage;
  assert.ok(Number.isSafeInteger(apiPage)&&Number.isSafeInteger(apiSize)&&Number.isSafeInteger(totalItems)&&Number.isSafeInteger(totalPages),
    'Real transaction pagination metadata must be numeric');
  assert.equal(apiPage,0); assert.equal(apiSize,10); assert.ok(totalItems>0,'The isolated browser fixture seeds completed fee-bearing history.');
  assert.equal(totalPages,Math.ceil(totalItems/apiSize));
  assert.equal(items.length,Math.min(totalItems,apiSize));
  realTransactionMetadata={page:apiPage,size:apiSize,totalItems,totalPages,itemCount:items.length};
  await atStep('assert rendered transaction pagination matches real API metadata','#pageRange',()=>expect(page.locator('#pageRange')).toHaveText(
    `Hiển thị 1–${Math.min(totalItems,apiSize)} / ${totalItems} giao dịch`));
  await atStep('assert rendered transaction count matches real API metadata','#transactionCount',()=>expect(page.locator('#transactionCount')).toHaveText(`${totalItems} giao dịch`));
  await atStep('assert rendered seeded state is not empty','#transactionStatus',()=>expect(page.locator('#transactionStatus')).toHaveText(''));
  await atStep('capture genuine isolated API state','real-api-history.png',()=>page.screenshot({path:path.join(output,'real-api-history.png'),fullPage:true}));
});
const stamp=day=>`${year}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}T12:00:00`;
  const rows=Array.from({length:31},(_,i)=>({id:i+1,source:i%2?'SUBSCRIPTION':'VISIT',occurredAt:stamp(i%20+1),reference:`TEST-${String(i+1).padStart(3,'0')}`,amount:(i+1)*1000}));
  const periodRows=q=>rows.filter(row=>{
    const date=row.occurredAt.slice(0,10);
    return q.get('type')==='range' ? date>=q.get('from')&&date<=q.get('to') : q.get('type')==='year' ? Number(q.get('year'))===year : Number(q.get('year'))===year&&Number(q.get('month'))===month;
  });
  const sums=items=>{ const visitRevenue=items.filter(r=>r.source==='VISIT').reduce((s,r)=>s+r.amount,0),subscriptionRevenue=items.filter(r=>r.source==='SUBSCRIPTION').reduce((s,r)=>s+r.amount,0);return {visitRevenue,subscriptionRevenue,totalRevenue:visitRevenue+subscriptionRevenue}; };
  const label=q=>q.get('type')==='month'?`Tháng ${q.get('month')}/${q.get('year')}`:q.get('type')==='year'?`Năm ${q.get('year')}`:`${q.get('from')} – ${q.get('to')}`;
let transactionMode='ok', summaryMode='ok', syntheticEmpty=false;
  await page.route('**/api/parking/revenue/summary?*',route=>{if(summaryMode==='error')return route.fulfill({status:500,json:{}});const q=new URL(route.request().url()).searchParams;return route.fulfill({json:{...sums(periodRows(q)),label:label(q),generatedAt:new Date().toISOString()}});});
  await page.route('**/api/parking/revenue/transactions?*',route=>{
    if(transactionMode==='error')return route.fulfill({status:500,json:{}});
    const q=new URL(route.request().url()).searchParams; let items=syntheticEmpty?[]:periodRows(q).filter(r=>(q.get('source')==='ALL'||r.source===q.get('source'))&&r.reference.includes((q.get('q')??'').toUpperCase()));
    items.sort((a,b)=>(a.occurredAt.localeCompare(b.occurredAt)||a.source.localeCompare(b.source)||a.id-b.id)*(q.get('sort')==='oldest'?1:-1));
    const size=Number(q.get('size')),p=Number(q.get('page')),totalItems=items.length;
    return route.fulfill({json:{items:items.slice(p*size,(p+1)*size),page:p,size,totalItems,totalPages:Math.ceil(totalItems/size),filteredRevenue:items.reduce((s,r)=>s+r.amount,0),generatedAt:new Date().toISOString()}});
  });
  await page.route('**/api/parking/revenue/trend',route=>route.fulfill({json:Array.from({length:12},(_,i)=>{const d=new Date(year,month-12+i,1);return {year:d.getFullYear(),month:d.getMonth()+1,...sums(i===11?rows:[])};})}));
  await page.goto('/'); await expect(page.locator('#pageRange')).toHaveText('Hiển thị 1–10 / 31 giao dịch');await expect(page.locator('#pageSize')).toHaveCount(0);
  await expect(page.locator('#pageNumbers button')).toHaveCount(4);
  await expect(page.locator('#pageNumbers [aria-current="page"]')).toHaveText('1');
  for(const [width,height]of [[1440,1000],[1280,800],[1024,768],[430,932],[375,812],[320,640]]){
     await page.setViewportSize({width,height});
    await page.screenshot({path:path.join(output,`viewport-${width}.png`),fullPage:true});
     assert.ok(await noHorizontalOverflow(),`Body overflow ${width}`);
  }
  if(finalStage){
    await page.setViewportSize({width:1440,height:900});
    await check('synthetic browser API fixture preserves the true-empty transaction state',async()=>{
      syntheticEmpty=true;
      await page.locator('#transactionFilter button[type="submit"]').click();
      await expect(page.locator('#pageRange')).toHaveText('Hiển thị 0–0 / 0 giao dịch');
      await expect(page.locator('#transactionStatus')).toHaveText('Chưa có giao dịch trong kỳ này.');
      await page.screenshot({path:path.join(output,'synthetic-empty.png'),fullPage:true});
      syntheticEmpty=false;
      await page.locator('#transactionFilter button[type="submit"]').click();
      await expect(page.locator('#pageRange')).toHaveText('Hiển thị 1–10 / 31 giao dịch');
    });
    await check('global total stays independent of pagination/search/source',async()=>{
      const total=await page.locator('#periodTotal').textContent();
      await page.locator('#nextPage').scrollIntoViewIfNeeded();
      const before=await pageY();
      await page.locator('#nextPage').click(); await expect(page.locator('#pageRange')).toHaveText('Hiển thị 11–20 / 31 giao dịch');
      await page.locator('#pageNumbers button', {hasText:'4'}).click();await expect(page.locator('#pageRange')).toHaveText('Hiển thị 31–31 / 31 giao dịch');
      await expect(page.locator('#pageNumbers [aria-current="page"]')).toHaveText('4');await expect(page.locator('#nextPage')).toBeDisabled();
      await page.locator('#pageNumbers button', {hasText:'2'}).click();await expect(page.locator('#pageRange')).toHaveText('Hiển thị 11–20 / 31 giao dịch');
      assert.ok(Math.abs(await pageY()-before)<20,'Pagination must not collapse the document and jump the viewport');
      assert.equal(await page.locator('#periodTotal').textContent(),total);
      const legacy=new URL(page.url());legacy.searchParams.set('size','50');await page.goto(legacy.toString());await expect(page.locator('#pageRange')).toHaveText('Hiển thị 11–20 / 31 giao dịch');await expect(page).not.toHaveURL(/size=/);
      await page.locator('#previousPage').click();await expect(page.locator('#pageRange')).toHaveText('Hiển thị 1–10 / 31 giao dịch');
      await page.locator('#transactionSearch').fill('TEST-001'); await page.locator('#transactionFilter button').click();
      await expect(page.locator('#pageRange')).toHaveText('Hiển thị 1–1 / 1 giao dịch');
      assert.equal(await page.locator('#periodTotal').textContent(),total);
      await page.locator('#transactionSource').selectOption('SUBSCRIPTION'); await page.locator('#transactionFilter button').click();
      await expect(page.locator('#transactionStatus')).toContainText('Không tìm thấy');await expect(page.locator('#clearTransactionFilters')).toBeVisible();
      assert.equal(await page.locator('#periodTotal').textContent(),total);
    });
    await check('URL reload and Back/Forward restore applied filters and pagination',async()=>{
      const selected=page.url(); await page.reload(); await expect(page.locator('#transactionSource')).toHaveValue('SUBSCRIPTION'); await expect(page.locator('#transactionSearch')).toHaveValue('TEST-001');
      await page.locator('#transactionSearch').fill(''); await page.locator('#transactionSource').selectOption('ALL'); await page.locator('#transactionFilter button').click();
      await expect(page.locator('#pageRange')).toContainText('/ 31 giao dịch');
      await page.goBack(); await expect(page).toHaveURL(selected); await expect(page.locator('#transactionSource')).toHaveValue('SUBSCRIPTION');
      await page.goForward(); await expect(page.locator('#transactionSearch')).toHaveValue(''); await expect(page.locator('#pageRange')).toContainText('/ 31 giao dịch');
      await page.locator('#transactionSearch').fill('NO_MATCH');await page.locator('#transactionFilter button').click();await expect(page.locator('#clearTransactionFilters')).toBeVisible();
      await page.locator('#clearTransactionFilters').click();await expect(page.locator('#pageRange')).toHaveText('Hiển thị 1–10 / 31 giao dịch');await expect(page.locator('#transactionSource')).toHaveValue('ALL');await expect(page.locator('#transactionSearch')).toHaveValue('');await expect(page.locator('#clearTransactionFilters')).toBeHidden();
    });
    await check('month drilldown does not jump page; explicit CTA moves focus',async()=>{
      await page.locator('.month-bar').last().scrollIntoViewIfNeeded();
      const before=await pageY(); await page.locator('.month-bar').last().click(); await expect(page.locator('#compositionPeriod')).toHaveText(`Tháng ${month}/${year}`);
      assert.ok(Math.abs(await pageY()-before)<15);
      await page.locator('#viewPeriodTransactions').click(); await expect(page.locator('#transactionsTitle')).toBeFocused();
    });
    await check('year/range/validation and reset',async()=>{
      await page.locator('#filterType').selectOption('year'); await page.locator('#revenueFilter button').click(); await expect(page.locator('#compositionPeriod')).toHaveText(`Năm ${year}`);
      await page.locator('#filterType').selectOption('range'); await page.locator('#filterFrom').fill(`${year}-01-02`); await page.locator('#filterTo').fill(`${year}-01-01`); await page.locator('#revenueFilter button').click(); await expect(page.locator('#filterStatus')).toContainText('ngày bắt đầu không sau');
      await page.locator('#resetPeriod').click(); await expect(page.locator('#compositionPeriod')).toHaveText(`Tháng ${month}/${year}`);
    });
    await check('table error/retry preserves period total and corrects out-of-range page',async()=>{
      const total=await page.locator('#periodTotal').textContent(); transactionMode='error'; await page.locator('#transactionFilter button').click(); await expect(page.locator('#retryTransactions')).toBeVisible(); assert.equal(await page.locator('#periodTotal').textContent(),total);
      transactionMode='ok'; await page.locator('#retryTransactions').click(); await expect(page.locator('#pageRange')).toContainText('/ 31 giao dịch');
      const url=new URL(page.url());url.searchParams.set('page','100'); await page.goto(url.toString()); await expect(page.locator('#pageRange')).toContainText('/ 31 giao dịch'); await expect(page).not.toHaveURL(/page=100(?:&|$)/);
    });
    await check('pending/failed summary blocks all table actions and never reuses old label',async()=>{
      let release;
      const pattern='**/api/parking/revenue/summary?*';
      const delayed=async route=>{await new Promise(resolve=>{release=resolve;});try{await route.fulfill({status:500,json:{}});}catch{}};
      await page.route(pattern,delayed);
      await page.locator('.month-bar').first().click();await expect.poll(()=>typeof release).toBe('function');
      await expect(page.locator('#transactionFilter button')).toBeDisabled();await expect(page.locator('#periodResults')).toBeHidden();
      release();await expect(page.locator('#filterStatus')).toContainText('Không tải được');
      await expect(page.locator('#transactionFilter button')).toBeDisabled();await expect(page.locator('#periodResults')).toBeHidden();
      await page.unroute(pattern,delayed);await page.locator('#resetPeriod').click();await expect(page.locator('#pageRange')).toContainText('/ 31 giao dịch');
    });
    await check('newer month selection supersedes delayed summary without stale rendering',async()=>{
      const releases=[];let count=0;const pattern='**/api/parking/revenue/summary?*';
      const delayed=async route=>{const i=count++;await new Promise(resolve=>{releases[i]=resolve;});try{const q=new URL(route.request().url()).searchParams;await route.fulfill({json:{...sums(periodRows(q)),label:label(q)}});}catch{}};
      await page.route(pattern,delayed);
      await page.locator('.month-bar').first().click();await expect.poll(()=>releases.length).toBe(1);
      await page.locator('.month-bar').last().click();await expect.poll(()=>releases.length).toBe(2);
      releases[0]();await expect(page.locator('#revenueFilter')).toHaveAttribute('aria-busy','true');releases[1]();
      await expect(page.locator('#compositionPeriod')).toHaveText(`Tháng ${month}/${year}`);await expect(page.locator('#pageRange')).toContainText('/ 31 giao dịch');
      await page.unroute(pattern,delayed);
    });
    checks.push({check:'cancellation during JSON body consumption cannot overwrite newer loading/results',status:'NOT RUN',reason:'Requires injecting a fetch/body-consumer replacement into the page, which is prohibited. The real delayed-GET supersession case remains enabled.'});
    checks.push({check:'sidebar nested geometry and navigation-only scroll',status:'NOT RUN',reason:'Required nested scroll dimensions are unavailable through allowed Page.getLayoutMetrics; previous direct scroll mutation is prohibited.'});
    checks.push({check:'shared scrollbar CSS values',status:'NOT RUN',reason:'Read-only CDP CSS node/style query is not implemented; no script-evaluation fallback.'});
    checks.push({check:'CSS.supports base-select feature detection',status:'NOT RUN',reason:'Feature detection would require page script execution; native select interaction/keyboard cases remain active.'});
    await check('custom native dropdown open/keyboard/Escape and viewport fit',async()=>{
      await page.locator('#filterType').scrollIntoViewIfNeeded(); await page.locator('#filterType').click();
      await page.screenshot({path:path.join(output,'dropdown-open.png'),fullPage:false});
      await page.keyboard.press('Escape'); await expect(page.locator('#filterType')).toBeFocused();
      await page.locator('#filterType').press('ArrowDown'); await page.keyboard.press('Escape');
      await page.setViewportSize({width:375,height:640});await page.locator('#transactionSource').scrollIntoViewIfNeeded();await page.locator('#transactionSource').click();
      await page.screenshot({path:path.join(output,'dropdown-mobile-bottom.png'),fullPage:false});await page.keyboard.press('Escape');
      await expect(page.locator('.transactions-panel .table-hint')).toBeVisible();
      await expect(page.locator('.sessions-panel .table-hint')).toBeVisible();
       assert.ok(await noHorizontalOverflow());
    });
    await check('mobile latest six months, drawer focus, and refresh',async()=>{
      await page.goto('/');await expect(page.locator('.month-bar')).toHaveCount(6); await expect(page.locator('.month-bar').last()).toHaveAttribute('aria-pressed','true');
      await page.locator('#openNavigation').click();await expect(page.locator('#navigationDialog')).toBeVisible();await page.keyboard.press('Escape');await expect(page.locator('#openNavigation')).toBeFocused();
      await page.locator('#refreshOverview').click(); await expect(page.locator('#snapshotTime')).toContainText('Cập nhật:');
    });
    await check('forced colors and reduced motion retain chart',async()=>{await page.emulateMedia({forcedColors:'active',reducedMotion:'reduce'}); await expect(page.locator('.bar-visit').first()).toHaveCSS('forced-color-adjust','none'); await page.screenshot({path:path.join(output,'forced-colors.png'),fullPage:true});});
    await check('no browser script exceptions',async()=>assert.deepEqual(errors,[]));
    await check('native POST logout and no-JavaScript mobile navigation remain usable',async()=>{
      await page.emulateMedia({forcedColors:'none',reducedMotion:'no-preference'});await page.setViewportSize({width:1440,height:900});
       page.once('dialog',dialog=>dialog.accept());await page.locator('.sidebar form[action="/logout"] button').click();await expect(page).toHaveURL(/\/login\?logout$/);
       const context=await browser.newContext({baseURL,javaScriptEnabled:false,viewport:{width:375,height:812}});
       await context.route('**/*',async route=>{const request=route.request();let target;try{target=new URL(request.url());}catch{guards.externalRequestsBlocked++;return route.abort();}if(target.origin!==baseURL){guards.externalRequestsBlocked++;return route.abort();}const method=request.method().toUpperCase();if(method==='GET'||method==='HEAD')return route.continue();if(method==='POST'&&target.pathname==='/login'){guards.loginPostsForwarded++;return route.continue();}if(method==='POST'&&target.pathname==='/logout'){guards.logoutPostsForwarded++;return route.continue();}guards.otherMutationsBlocked++;return route.abort();});
       const native=await context.newPage();
      await native.goto('/login');await native.locator('#username').fill('lumipark-browser-manager');await native.locator('#password').fill(fixture.match(/PASSWORD = "([^"]+)"/)[1]);await native.locator('#loginSubmit').click();
      await expect(native.locator('#currentToday')).toBeVisible();await expect(native.locator('noscript > p.feedback')).toContainText('Bật JavaScript');
       await native.locator('.noscript-navigation > summary').click();await expect(native.locator('.noscript-navigation a[aria-current="page"]')).toBeVisible();
       native.once('dialog',dialog=>dialog.accept());await native.locator('.noscript-navigation form[action="/logout"] button').click();await expect(native).toHaveURL(/\/login\?logout$/);await context.close();
       assert.equal(guards.logoutPostsForwarded,2,'Only the two dedicated synthetic contexts may submit logout');
       checks.push({check:'dedicated synthetic-context native POST logout',status:'EXECUTED',posts:guards.logoutPostsForwarded,note:'Both logout POSTs were explicit native actions in isolated synthetic H2 contexts; no business action.'});
    });
  }
}catch(error){checks.push({status:'FAIL',step:activeStep.name,category:activeStep.category,location:activeStep.location,errorKind:error?.name??'Error'});process.exitCode=1;}
finally{clearTimeout(watchdog);await browser?.close().catch(()=>{});const sourceAfter=await sourceIdentity().catch(()=>null);const sourceStable=sourceAfter!==null&&JSON.stringify(sourceBefore)===JSON.stringify(sourceAfter);if(savedTemp===undefined)delete process.env.TEMP;else process.env.TEMP=savedTemp;if(savedTmp===undefined)delete process.env.TMP;else process.env.TMP=savedTmp;const realApi={summaryLabel:realSummaryLabel,transactions:realTransactionMetadata};checks.push({preflight:{controllerPidAlive:true,port,listenerProbe,stopMarker:'absent',executablePath,tempLocation:path.relative(root,tempDirectory).replaceAll(path.sep,'/')},sourceIdentity:{stable:sourceStable,before:sourceBefore,after:sourceAfter},guards,apiStatuses,realApi});await writeFile(path.join(output,'checks.json'),JSON.stringify(checks,null,2));console.log(JSON.stringify({checks:checks.map(({check,status,step,category,location})=>({check,status,step,category,location})),apiStatuses,realApi,sourceStable,guards}));if(!sourceStable)process.exitCode=1;}
