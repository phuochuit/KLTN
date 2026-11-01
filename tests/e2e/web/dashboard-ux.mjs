// Real isolated Spring APIs first; explicit browser-only synthetic data for larger UX states afterwards.
import { createRequire } from 'node:module';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=fileURLToPath(new URL('../../../',import.meta.url));
const require=createRequire(path.join(root,'spring-web/package.json'));
const {chromium,expect}=require('@playwright/test');
const run=path.resolve(process.argv[2]??''); assert.ok(run.startsWith(path.join(root,'.artifacts/test-runs')+path.sep));
const stage=process.argv[3]??'final'; assert.match(stage,/^[a-z0-9-]+$/);
const output=path.join(run,stage); await mkdir(output,{recursive:false});
const port=(await readFile(path.join(run,'server.port'),'utf8')).trim(); assert.match(port,/^\d{1,5}$/);
const baseURL=`http://127.0.0.1:${port}`;
const fixture=await readFile(path.join(root,'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'),'utf8');
const browser=await chromium.launch({channel:'msedge',headless:true});
const checks=[];
const check=async(name,fn)=>{await fn();checks.push({check:name,status:'PASS'});};
try {
  const page=await browser.newPage({baseURL,viewport:{width:1440,height:1000}});
  const errors=[]; page.on('pageerror',e=>errors.push(e.name));
  await page.goto('/login'); await page.locator('#username').fill('lumipark-browser-manager'); await page.locator('#password').fill(fixture.match(/PASSWORD = "([^"]+)"/)[1]); await page.locator('#loginSubmit').click();
  await expect(page).toHaveURL(url=>url.origin===baseURL&&url.pathname==='/');
  await check('real isolated overview/summary/transaction API and empty UI',async()=>{
    await expect(page.locator('#compositionPeriod')).toContainText('Tháng');
    await expect(page.locator('#transactionStatus')).toContainText('Chưa có giao dịch');
    await expect(page.locator('#snapshotTime')).toContainText('Cập nhật:');
    for(const endpoint of ['/api/dashboard/overview','/api/parking/revenue/trend','/api/parking/revenue/summary?type=month&year=2026&month=10','/api/parking/revenue/transactions?type=month&year=2026&month=10&page=0&size=20']) assert.ok((await page.request.get(endpoint)).ok());
    await page.screenshot({path:path.join(output,'real-api-empty.png'),fullPage:true});
  });
  const now=new Date(), year=now.getFullYear(), month=now.getMonth()+1;
  const stamp=day=>`${year}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}T12:00:00`;
  const rows=Array.from({length:31},(_,i)=>({id:i+1,source:i%2?'SUBSCRIPTION':'VISIT',occurredAt:stamp(i%20+1),reference:`TEST-${String(i+1).padStart(3,'0')}`,amount:(i+1)*1000}));
  const periodRows=q=>rows.filter(row=>{
    const date=row.occurredAt.slice(0,10);
    return q.get('type')==='range' ? date>=q.get('from')&&date<=q.get('to') : q.get('type')==='year' ? Number(q.get('year'))===year : Number(q.get('year'))===year&&Number(q.get('month'))===month;
  });
  const sums=items=>{ const visitRevenue=items.filter(r=>r.source==='VISIT').reduce((s,r)=>s+r.amount,0),subscriptionRevenue=items.filter(r=>r.source==='SUBSCRIPTION').reduce((s,r)=>s+r.amount,0);return {visitRevenue,subscriptionRevenue,totalRevenue:visitRevenue+subscriptionRevenue}; };
  const label=q=>q.get('type')==='month'?`Tháng ${q.get('month')}/${q.get('year')}`:q.get('type')==='year'?`Năm ${q.get('year')}`:`${q.get('from')} – ${q.get('to')}`;
  let transactionMode='ok', summaryMode='ok';
  await page.route('**/api/parking/revenue/summary?*',route=>{if(summaryMode==='error')return route.fulfill({status:500,json:{}});const q=new URL(route.request().url()).searchParams;return route.fulfill({json:{...sums(periodRows(q)),label:label(q),generatedAt:new Date().toISOString()}});});
  await page.route('**/api/parking/revenue/transactions?*',route=>{
    if(transactionMode==='error')return route.fulfill({status:500,json:{}});
    const q=new URL(route.request().url()).searchParams; let items=periodRows(q).filter(r=>(q.get('source')==='ALL'||r.source===q.get('source'))&&r.reference.includes((q.get('q')??'').toUpperCase()));
    items.sort((a,b)=>(a.occurredAt.localeCompare(b.occurredAt)||a.source.localeCompare(b.source)||a.id-b.id)*(q.get('sort')==='oldest'?1:-1));
    const size=Number(q.get('size')),p=Number(q.get('page')),totalItems=items.length;
    return route.fulfill({json:{items:items.slice(p*size,(p+1)*size),page:p,size,totalItems,totalPages:Math.ceil(totalItems/size),filteredRevenue:items.reduce((s,r)=>s+r.amount,0),generatedAt:new Date().toISOString()}});
  });
  await page.route('**/api/parking/revenue/trend',route=>route.fulfill({json:Array.from({length:12},(_,i)=>{const d=new Date(year,month-12+i,1);return {year:d.getFullYear(),month:d.getMonth()+1,...sums(i===11?rows:[])};})}));
  await page.goto('/'); await expect(page.locator('#pageRange')).toHaveText('Hiển thị 1–10 / 31 giao dịch');await expect(page.locator('#pageSize')).toHaveCount(0);
  await expect(page.locator('#pageNumbers button')).toHaveCount(4);
  await expect(page.locator('#pageNumbers [aria-current="page"]')).toHaveText('1');
  for(const [width,height]of [[1440,1000],[1280,800],[1024,768],[430,932],[375,812],[320,640]]){
    await page.setViewportSize({width,height}); await page.evaluate(()=>document.fonts.ready);
    await page.screenshot({path:path.join(output,`viewport-${width}.png`),fullPage:true});
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`Body overflow ${width}`);
  }
  if(stage==='final'){
    await page.setViewportSize({width:1440,height:900});
    await check('global total stays independent of pagination/search/source',async()=>{
      const total=await page.locator('#periodTotal').textContent();
      await page.locator('#nextPage').scrollIntoViewIfNeeded();
      const before=await page.evaluate(()=>scrollY);
      await page.locator('#nextPage').click(); await expect(page.locator('#pageRange')).toHaveText('Hiển thị 11–20 / 31 giao dịch');
      await page.locator('#pageNumbers button', {hasText:'4'}).click();await expect(page.locator('#pageRange')).toHaveText('Hiển thị 31–31 / 31 giao dịch');
      await expect(page.locator('#pageNumbers [aria-current="page"]')).toHaveText('4');await expect(page.locator('#nextPage')).toBeDisabled();
      await page.locator('#pageNumbers button', {hasText:'2'}).click();await expect(page.locator('#pageRange')).toHaveText('Hiển thị 11–20 / 31 giao dịch');
      assert.ok(Math.abs(await page.evaluate(()=>scrollY)-before)<20,'Pagination must not collapse the document and jump the viewport');
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
      const before=await page.evaluate(()=>scrollY); await page.locator('.month-bar').last().click(); await expect(page.locator('#compositionPeriod')).toHaveText(`Tháng ${month}/${year}`);
      assert.ok(Math.abs(await page.evaluate(()=>scrollY)-before)<15);
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
    await check('cancellation during JSON body consumption cannot overwrite newer loading/results',async()=>{
      // Test-only body-consumer stub: real headers arrive first; JSON remains pending until release/abort.
      await page.evaluate(()=>{
        const original=window.fetch; window.__restoreFetch=()=>{window.fetch=original;};
        window.__bodyReads=[];window.__bodyAborts=0;
        window.fetch=async(...args)=>{
          const response=await original(...args);
          if(!String(args[0]).includes('/revenue/summary?')||window.__bodyReads.length>=2)return response;
          return {ok:response.ok,status:response.status,redirected:response.redirected,headers:response.headers,json:()=>new Promise((resolve,reject)=>{
            window.__bodyReads.push(()=>resolve(response.json()));
            const abort=()=>{window.__bodyAborts++;reject(new DOMException('Body read cancelled','AbortError'));};
            if(args[1]?.signal?.aborted)abort();else args[1]?.signal?.addEventListener('abort',abort,{once:true});
          })};
        };
      });
      try{
        await page.locator('.month-bar').first().click();await expect.poll(()=>page.evaluate(()=>window.__bodyReads.length)).toBe(1);
        await page.locator('.month-bar').last().click();await expect.poll(()=>page.evaluate(()=>window.__bodyReads.length)).toBe(2);
        await expect.poll(()=>page.evaluate(()=>window.__bodyAborts)).toBe(1);
        await expect(page.locator('#filterStatus')).toHaveText('Đang tải tổng doanh thu kỳ đã chọn…');
        await expect(page.locator('#revenueFilter')).toHaveAttribute('aria-busy','true');
        await page.evaluate(()=>window.__bodyReads[1]());
        await expect(page.locator('#compositionPeriod')).toHaveText(`Tháng ${month}/${year}`);await expect(page.locator('#pageRange')).toContainText('/ 31 giao dịch');
      }finally{await page.evaluate(()=>{window.__restoreFetch();delete window.__restoreFetch;delete window.__bodyReads;delete window.__bodyAborts;});}
    });
    await check('sidebar scroll only navigation, fixed brand/footer at short height',async()=>{
      await page.setViewportSize({width:1280,height:640}); await page.locator('.sidebar .nav-extra').evaluate(n=>{n.open=true;});
      const info=await page.evaluate(()=>{const s=document.querySelector('.sidebar'),nav=s.querySelector('nav'),footer=s.querySelector('.navigation-bottom');return {side:s.clientHeight,scroll:s.scrollHeight,navScroll:nav.scrollHeight,navHeight:nav.clientHeight,footer:footer.getBoundingClientRect().bottom,viewport:innerHeight};});
      assert.ok(info.scroll<=info.side+2);assert.ok(info.navScroll>info.navHeight);assert.ok(info.footer<=info.viewport);
      await page.locator('.sidebar nav').evaluate(n=>{n.scrollTop=n.scrollHeight;});
      await expect(page.locator('.sidebar form[action="/logout"] button')).toBeVisible();
      await page.screenshot({path:path.join(output,'sidebar-short.png'),fullPage:false});
    });
    await check('page, sidebar, chart and tables share visible thin scrollbar colors',async()=>{
      const styles=await page.evaluate(()=>{
        const css=node=>({width:getComputedStyle(node).scrollbarWidth,color:getComputedStyle(node).scrollbarColor});
        return [document.documentElement,document.querySelector('.sidebar .dashboard-navigation'),document.querySelector('.revenue-chart'),document.querySelector('.transactions-panel .table-scroll')].map(css);
      });
      assert.ok(styles.every(style=>style.width==='thin'&&style.color===styles[0].color),JSON.stringify(styles));
    });
    await check('custom native dropdown open/keyboard/Escape and viewport fit',async()=>{
      assert.ok(await page.evaluate(()=>CSS.supports('appearance','base-select')));
      await page.locator('#filterType').scrollIntoViewIfNeeded(); await page.locator('#filterType').click();
      await page.screenshot({path:path.join(output,'dropdown-open.png'),fullPage:false});
      await page.keyboard.press('Escape'); await expect(page.locator('#filterType')).toBeFocused();
      await page.locator('#filterType').press('ArrowDown'); await page.keyboard.press('Escape');
      await page.setViewportSize({width:375,height:640});await page.locator('#transactionSource').scrollIntoViewIfNeeded();await page.locator('#transactionSource').click();
      await page.screenshot({path:path.join(output,'dropdown-mobile-bottom.png'),fullPage:false});await page.keyboard.press('Escape');
      await expect(page.locator('.transactions-panel .table-hint')).toBeVisible();
      await expect(page.locator('.sessions-panel .table-hint')).toBeVisible();
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
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
      const context=await browser.newContext({baseURL,javaScriptEnabled:false,viewport:{width:375,height:812}});const native=await context.newPage();
      await native.goto('/login');await native.locator('#username').fill('lumipark-browser-manager');await native.locator('#password').fill(fixture.match(/PASSWORD = "([^"]+)"/)[1]);await native.locator('#loginSubmit').click();
      await expect(native.locator('#currentToday')).toBeVisible();await expect(native.locator('noscript > p.feedback')).toContainText('Bật JavaScript');
      await native.locator('.noscript-navigation > summary').click();await expect(native.locator('.noscript-navigation a[aria-current="page"]')).toBeVisible();
      await native.locator('.noscript-navigation form[action="/logout"] button').click();await expect(native).toHaveURL(/\/login\?logout$/);await context.close();
    });
  }
}catch(error){checks.push({status:'FAIL',type:error.name,message:error.message});process.exitCode=1;}
finally{await writeFile(path.join(output,'checks.json'),JSON.stringify(checks,null,2));console.log(JSON.stringify(checks,null,2));await browser.close();}
