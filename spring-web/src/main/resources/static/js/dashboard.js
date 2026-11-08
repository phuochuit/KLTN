import { money, localDate, revenueParts, periodQuery, readDashboardState, dashboardSearch } from './dashboard-model.mjs';
const $ = selector => document.querySelector(selector);
const text = (selector, value) => { $(selector).textContent = value; };
const element = (tag, value, className) => {
  const node = document.createElement(tag);
  if (value !== undefined) node.textContent = value;
  if (className) node.className = className;
  return node;
};
const row = values => { const tr = document.createElement('tr'); values.forEach(value => tr.append(element('td',value))); return tr; };
const dateText = value => { const date = new Date(value); return value && !Number.isNaN(date.getTime()) ? date.toLocaleString('vi-VN') : '—'; };
const navigation = $('#navigationDialog');
$('#openNavigation').addEventListener('click', () => navigation.showModal());
$('#closeNavigation').addEventListener('click', () => navigation.close());
document.querySelectorAll('.dashboard-logout').forEach(form => form.addEventListener('submit', async event => {
  event.preventDefault();
  const question = 'Bạn muốn đăng xuất khỏi hệ thống?';
  const accepted = window.ParkingUi
    ? await window.ParkingUi.confirm(question, 'Đăng xuất')
    : window.confirm(question);
  if (accepted) form.submit();
}));

const now = new Date();
for (let month = 1; month <= 12; month++) $('#filterMonth').add(new Option('Tháng ' + month, month));
for (let year = now.getFullYear(); year >= now.getFullYear() - 10; year--) $('#filterYear').add(new Option(year,year));
const defaults = () => readDashboardState('',new Date(),matchMedia('(max-width: 47.999rem)').matches ? 6 : 12);
let state;
try { state = readDashboardState(location.search, now, defaults().months); }
catch { state = defaults(); text('#overviewStatus','Địa chỉ chứa bộ lọc không hợp lệ; đã khôi phục tháng hiện tại.'); }
let lastSearch = location.search;
function controls() {
  if (![...$('#filterYear').options].some(option => option.value === state.year)) $('#filterYear').add(new Option(state.year,state.year));
  for (const name of ['type','year','month','from','to']) $({type:'#filterType',year:'#filterYear',month:'#filterMonth',from:'#filterFrom',to:'#filterTo'}[name]).value = state[name];
  $('#transactionSearch').value = state.q; $('#transactionSource').value = state.source; $('#transactionSort').value = state.sort;
  $('#trendWindow').value = String(state.months); changeType();
  const exportParams = new URLSearchParams({type:state.type});
  if (state.type === 'month') { exportParams.set('year',state.year); exportParams.set('month',state.month); }
  else if (state.type === 'year') exportParams.set('year',state.year);
  else { exportParams.set('from',state.from); exportParams.set('to',state.to); }
  const exportLink = $('#exportRevenueCsv');
  if (exportLink) exportLink.href = '/revenue/export-csv?' + exportParams;
}
function changeType() {
  const type = $('#filterType').value;
  $('#monthField').hidden = type !== 'month'; $('#yearField').hidden = type === 'range';
  $('#fromField').hidden = $('#toField').hidden = type !== 'range';
  $('#filterFrom').required = $('#filterTo').required = type === 'range';
}
$('#filterType').addEventListener('change',changeType);
function saveURL(mode = 'push') {
  const url = new URL(location.href); url.search = dashboardSearch(state);
  if (url.search !== location.search) history[mode === 'replace' ? 'replaceState' : 'pushState'](null,'',url);
  lastSearch = location.search;
}
async function json(url, signal) {
  const response = await fetch(url,{ signal,headers:{Accept:'application/json'} });
  if (response.status === 401 || response.redirected) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
  if (response.status === 403) throw new Error('Bạn không có quyền xem dữ liệu này.');
  if (response.status === 400) throw new Error('Bộ lọc không hợp lệ. Kiểm tra kỳ, khoảng ngày và từ khóa.');
  if (!response.ok) throw new Error('Không tải được dữ liệu. Vui lòng thử lại.');
  if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Phản hồi không hợp lệ. Vui lòng tải lại trang.');
  try { return await response.json(); }
  catch(error) { if (error.name === 'AbortError' || signal?.aborted) throw error; throw new Error('Dữ liệu phản hồi không hợp lệ. Vui lòng thử lại.'); }
}
function selectedBars() {
  const selected = state.type === 'month' ? `${state.year}-${state.month}` : '';
  document.querySelectorAll('.month-bar').forEach(button => button.setAttribute('aria-pressed',String(button.dataset.period === selected)));
}
function clearSummary() {
  for (const id of ['#periodTotal','#compositionTotal','#visitAmount','#subscriptionAmount','#visitPercent','#subscriptionPercent']) text(id,'—');
  $('#revenueDonut').style.removeProperty('background');
  text('#compositionPeriod','Chưa có dữ liệu kỳ đang chọn'); text('#compositionNote','');
}
function clearTable() {
  $('#periodResults').hidden = true; $('#detailBody').replaceChildren();
  $('#previousPage').disabled = $('#nextPage').disabled = true;
  $('#pageNumbers').replaceChildren(); text('#pageRange','Đang tải…');
}
function renderPages(total) {
  const pages = $('#pageNumbers'); pages.replaceChildren();
  const shown = [...new Set([1,2,3,total,state.page,state.page+1,state.page+2])].filter(n => n >= 1 && n <= total).sort((a,b) => a-b);
  let previous = 0;
  for (const number of shown) {
    if (number > previous + 1) {
      if (number === previous + 2) {
        const missing=element('button',String(number-1),'page-number'); missing.type='button'; missing.setAttribute('aria-label',`Trang ${number-1}`);
        missing.addEventListener('click',()=>goToPage(number-2)); pages.append(missing);
      } else { const gap=element('span','…','page-ellipsis'); gap.setAttribute('aria-hidden','true'); pages.append(gap); }
    }
    const button=element('button',String(number),'page-number'); button.type='button'; button.setAttribute('aria-label',`Trang ${number}`);
    if (number === state.page+1) button.setAttribute('aria-current','page');
    button.addEventListener('click',()=>goToPage(number-1)); pages.append(button); previous=number;
  }
}
function goToPage(page) { if (page === state.page) return; state={...state,page}; saveURL(); loadTable(); }
let summaryRequest;
let tableRequest;
let appliedLabel = '';
let resolvedPeriod = null;
let tableLayoutKey = '';
async function loadPeriod(historyMode = 'push') {
  summaryRequest?.abort(); tableRequest?.abort();
  tableRequest = null; appliedLabel = ''; resolvedPeriod = null; tableLayoutKey = '';
  $('#transactionFilter button[type="submit"]').disabled = true;
  $('#periodResults').style.minHeight = '';
  const request = new AbortController(); summaryRequest = request;
  const button = $('#revenueFilter button[type="submit"]');
  clearSummary(); clearTable(); $('#filterStatus').classList.remove('is-error');
  try {
    const params = periodQuery(state); saveURL(historyMode); selectedBars(); controls();
    button.disabled = true; button.textContent = 'Đang tải kỳ…';
    $('#revenueFilter').setAttribute('aria-busy','true');
    text('#filterStatus','Đang tải tổng doanh thu kỳ đã chọn…'); text('#transactionStatus','Đang chờ kỳ doanh thu…');
    const data = await json('/api/parking/revenue/summary?' + params,request.signal);
    if (request !== summaryRequest) return;
    const parts = revenueParts(data);
    if (typeof data.label !== 'string' || !data.label) throw new Error('Thiếu thông tin kỳ doanh thu.');
    appliedLabel = data.label; resolvedPeriod = {...state};
    text('#compositionPeriod',appliedLabel); text('#compositionTotal',money(parts.total)); text('#periodTotal',money(parts.total));
    text('#visitAmount',money(parts.visit)); text('#subscriptionAmount',money(parts.subscription));
    const percent = value => `${value.toLocaleString('vi-VN',{maximumFractionDigits:1})}%`;
    text('#visitPercent',parts.total ? percent(parts.visitPercent) : 'Không phát sinh');
    text('#subscriptionPercent',parts.total ? percent(100-parts.visitPercent) : 'Không phát sinh');
    if (parts.total) $('#revenueDonut').style.background = `conic-gradient(var(--d-blue) 0% ${parts.visitPercent}%,var(--d-purple) ${parts.visitPercent}% 100%)`;
    text('#compositionNote',parts.total ? '' : 'Không phát sinh doanh thu trong kỳ này.');
    text('#filterStatus','');
    text('#viewPeriodTransactions',`Xem giao dịch ${appliedLabel.toLowerCase()} ↓`);
    await loadTable();
  } catch(error) {
    if (error.name === 'AbortError' || request !== summaryRequest || request.signal.aborted) return;
    clearSummary(); text('#filterStatus',error.message + ' Chọn Áp dụng kỳ để thử lại.'); $('#filterStatus').classList.add('is-error');
    text('#transactionStatus','Chưa tải được kỳ. Vui lòng áp dụng lại bộ lọc kỳ.'); text('#pageRange','Chưa có kết quả');
  } finally {
    if (request === summaryRequest) { button.disabled = false; button.textContent = 'Áp dụng kỳ'; $('#revenueFilter').setAttribute('aria-busy','false'); }
  }
}
async function loadTable() {
  if (!resolvedPeriod || !appliedLabel) return;
  const period = resolvedPeriod, label = appliedLabel;
  tableRequest?.abort(); const request = new AbortController(); tableRequest = request;
  const oldHeight = $('#periodResults').getBoundingClientRect().height;
  const layoutKey = periodQuery(period).toString() + '|' + [state.q,state.source,state.sort,state.size].join('|');
  $('#previousPage').disabled = $('#nextPage').disabled = true;
  $('#pageNumbers').querySelectorAll('button').forEach(button=>button.disabled=true);
  text('#pageRange','Đang tải…');
  $('#retryTransactions').hidden = true; $('#clearTransactionFilters').hidden = true; $('#transactionStatus').classList.remove('is-error');
  const button = $('#transactionFilter button[type="submit"]'); button.disabled = true;
  $('#periodResults').setAttribute('aria-busy','true');
  text('#transactionStatus',oldHeight ? 'Đang tải trang mới… Dòng mờ là trang trước.' : 'Đang tải giao dịch…');
  try {
    const params = periodQuery(period);
    for (const name of ['q','source','sort','page','size']) params.set(name,String(state[name]));
    const data = await json('/api/parking/revenue/transactions?' + params,request.signal);
    if (request !== tableRequest) return;
    if (!Array.isArray(data.items) || !Number.isSafeInteger(data.totalItems) || !Number.isInteger(data.totalPages) || data.totalItems < 0) throw new Error('Dữ liệu phân trang không hợp lệ.');
    if (state.page > 0 && state.page >= data.totalPages) { state = {...state,page:Math.max(0,data.totalPages-1)}; saveURL('replace'); return await loadTable(); }
    const rows = data.items.map(item => {
      if (!['VISIT','SUBSCRIPTION'].includes(item.source) || !Number.isFinite(Number(item.amount)) || item.amount === null || Number(item.amount) < 0) throw new Error('Số tiền giao dịch không hợp lệ.');
      return row([dateText(item.occurredAt),item.source === 'VISIT' ? 'Vé lượt' : 'Gói tháng',item.reference || '—',money(Number(item.amount))]);
    });
    if (!rows.length) { const tr = row([state.q || state.source !== 'ALL' ? 'Không tìm thấy giao dịch phù hợp.' : 'Chọn kỳ khác để xem giao dịch.']); tr.firstChild.colSpan=4; tr.firstChild.className='empty-cell'; rows.push(tr); }
    $('#periodResults').style.minHeight = tableLayoutKey === layoutKey && oldHeight ? `${oldHeight}px` : '';
    tableLayoutKey = layoutKey;
    $('#detailBody').replaceChildren(...rows); text('#detailTitle',label);
    text('#transactionCount',`${data.totalItems} giao dịch`);
    const from = data.totalItems ? state.page * state.size + 1 : 0;
    text('#pageRange',`Hiển thị ${from}–${data.totalItems ? from + data.items.length - 1 : 0} / ${data.totalItems} giao dịch`);
    renderPages(data.totalPages);
    $('#previousPage').disabled = state.page === 0; $('#nextPage').disabled = state.page + 1 >= data.totalPages;
    text('#transactionStatus',data.totalItems ? '' : state.q || state.source !== 'ALL' ? 'Không tìm thấy giao dịch phù hợp.' : 'Chưa có giao dịch trong kỳ này.');
    $('#clearTransactionFilters').hidden = !!data.totalItems || (!state.q && state.source === 'ALL');
    $('#periodResults').hidden = false;
  } catch(error) {
    if (error.name === 'AbortError' || request !== tableRequest || request.signal.aborted) return;
    $('#periodResults').hidden = true;
    $('#pageNumbers').replaceChildren();
    text('#transactionStatus',error.message); $('#transactionStatus').classList.add('is-error'); text('#pageRange','Chưa tải được trang'); $('#retryTransactions').hidden=false;
  } finally {
    if (request === tableRequest) { button.disabled=false; $('#periodResults').setAttribute('aria-busy','false'); }
  }
}
$('#revenueFilter').addEventListener('submit',event => {
  event.preventDefault();
  try { const next = {...state,...Object.fromEntries(new FormData(event.currentTarget)),page:0}; periodQuery(next); state=next; loadPeriod(); }
  catch(error) { text('#filterStatus',error.message); $('#filterStatus').classList.add('is-error'); }
});
$('#transactionFilter').addEventListener('submit',event => {
  event.preventDefault(); if (!appliedLabel) return;
  state={...state,...Object.fromEntries(new FormData(event.currentTarget)),page:0}; saveURL(); loadTable();
});
for (const [id,delta] of [['#previousPage',-1],['#nextPage',1]]) $(id).addEventListener('click',() => goToPage(state.page+delta));
$('#retryTransactions').addEventListener('click',loadTable);
$('#clearTransactionFilters').addEventListener('click',() => { state={...state,q:'',source:'ALL',page:0}; $('#transactionSearch').value=''; $('#transactionSource').value='ALL'; saveURL(); loadTable(); });
$('#resetPeriod').addEventListener('click',() => { state={...defaults(),size:state.size,months:state.months}; loadPeriod(); });
$('#showToday').addEventListener('click',() => { state={...defaults(),months:state.months,type:'range',from:localDate(new Date()),to:localDate(new Date())}; loadPeriod(); });
$('#transactionsTitle').tabIndex=-1;
$('#viewPeriodTransactions').addEventListener('click',() => $('#transactionsTitle').focus());

let overviewRequest;
async function loadOverview() {
  overviewRequest?.abort(); const request=new AbortController(); overviewRequest=request;
  $('#refreshOverview').disabled=true; $('#refreshOverview').textContent='Đang cập nhật…';
  try {
    const data=await json('/api/dashboard/overview',request.signal);
    if (request!==overviewRequest) return;
    for (const [key,id] of [['openCount','#currentOpen'],['vehicleCount','#currentVehicles'],['householdCount','#currentHouseholds']]) text(id,new Intl.NumberFormat('vi-VN').format(data[key]));
    text('#currentToday',money(Number(data.todayRevenue))); text('#currentMonth',money(Number(data.monthRevenue)));
    const statusText={OPEN:'Trong bãi',COMPLETED:'Đã ra',CANCELLED:'Đã hủy'};
    const rows=data.recentSessions.map(s=>row([s.entryPlate || '—',dateText(s.entryTime),dateText(s.exitTime),statusText[s.status] || s.status,money(Number(s.fee))]));
    if (!rows.length) { const tr=row(['Chưa có lượt xe.']); tr.firstChild.colSpan=5; tr.firstChild.className='empty-cell'; rows.push(tr); }
    $('#recentSessionsBody').replaceChildren(...rows); text('#snapshotTime','Cập nhật: '+dateText(data.generatedAt)); text('#overviewStatus','');
  } catch(error) { if(error.name!=='AbortError' && request===overviewRequest && !request.signal.aborted) text('#overviewStatus',error.message+' Các KPI vẫn là lần tải thành công trước, không phải số liệu mới.'); }
  finally { if(request===overviewRequest) { $('#refreshOverview').disabled=false; $('#refreshOverview').textContent='Làm mới tổng quan'; } }
}
$('#refreshOverview').addEventListener('click',loadOverview);

let trendRequest;
let series=[];
function revealSelectedMonth() {
  const chart=$('#revenueChart');
  const selected=chart.querySelector('[aria-pressed="true"]') || chart.querySelector('.month-bar:last-child');
  if (!selected) return;
  const target=selected.getBoundingClientRect(),bounds=chart.getBoundingClientRect();
  chart.scrollLeft=Math.max(0,chart.scrollLeft+target.left-bounds.left-chart.clientWidth/2+target.width/2);
}
new ResizeObserver(revealSelectedMonth).observe($('#revenueChart'));
function drawTrend() {
  const chart=$('#revenueChart'); chart.replaceChildren(); $('#chartDataBody').replaceChildren();
  const visible=series.slice(-state.months);
  const maximum=Math.max(...visible.map(d=>d.total),0);
  chart.classList.toggle('is-empty',visible.length > 0 && maximum === 0);
  text('#trendStatus',maximum ? '' : 'Không phát sinh doanh thu trong các tháng đang xem.');
  if (!visible.length) return;
  const order=maximum ? 10**Math.floor(Math.log10(maximum)) : 1;
  const scale=maximum ? Math.ceil(maximum/order)*order : 1;
  const axis=element('div',undefined,'chart-axis');
  const compact=value=>value>=1e9 ? `${value/1e9} tỷ` : value>=1e6 ? `${value/1e6} triệu` : value>=1e3 ? `${value/1e3} nghìn` : String(value);
  [scale,scale/2,0].forEach(value=>axis.append(element('span',maximum ? compact(value) : value===0 ? '0' : '—')));
  const plot=element('div',undefined,'chart-plot'); plot.style.setProperty('--months',visible.length);
  visible.forEach(d=>{
    const label=`${d.month}/${d.year}`; const button=element('button',undefined,'month-bar'); button.type='button'; button.dataset.period=`${d.year}-${d.month}`;
    button.setAttribute('aria-label',`${label}: vé lượt ${money(d.visit)}, gói tháng ${money(d.subscription)}, tổng ${money(d.total)}. Xem chi tiết.`);
    const area=element('span',undefined,'bar-area'); const stack=element('span',undefined,'bar-stack'); stack.style.height=`${d.total/scale*100}%`;
    const visit=element('span',undefined,'bar-visit'); visit.style.height=`${d.total ? d.visit/d.total*100 : 0}%`;
    const subscription=element('span',undefined,'bar-subscription'); subscription.style.height=`${d.total ? d.subscription/d.total*100 : 0}%`;
    stack.append(subscription,visit); area.append(stack);
    const tooltip=element('span',`${label}\nVé lượt: ${money(d.visit)}\nGói tháng: ${money(d.subscription)}\nTổng: ${money(d.total)}`,'bar-tooltip'); tooltip.setAttribute('aria-hidden','true');
    button.append(area,element('span',`${d.month}/${String(d.year).slice(-2)}`,'month-label'),tooltip);
    for (const event of ['mouseenter','focus']) button.addEventListener(event,()=>button.classList.remove('tooltip-dismissed'));
    button.addEventListener('click',()=>{ state={...state,type:'month',year:String(d.year),month:String(d.month),page:0}; loadPeriod(); });
    plot.append(button); $('#chartDataBody').append(row([label,money(d.visit),money(d.subscription),money(d.total)]));
  });
  chart.append(axis,plot); selectedBars();
  // Only this internal chart is repositioned; never scroll the page after choosing a month.
  revealSelectedMonth();
}
async function loadTrend() {
  trendRequest?.abort(); const request=new AbortController(); trendRequest=request;
  $('#revenueChart').setAttribute('aria-busy','true'); $('#retryTrend').disabled=true; text('#trendStatus','Đang tải xu hướng…');
  try {
    const data=await json('/api/parking/revenue/trend',request.signal);
    if(request!==trendRequest) return;
    if(!Array.isArray(data)) throw new Error('Dữ liệu biểu đồ không hợp lệ.');
    series=data.map(d=>({...d,...revenueParts(d)}));
    if(series.some(d=>!Number.isInteger(d.year)||!Number.isInteger(d.month)||d.month<1||d.month>12)) throw new Error('Kỳ biểu đồ không hợp lệ.');
    drawTrend();
  } catch(error) { if(error.name!=='AbortError' && request===trendRequest && !request.signal.aborted) { $('#revenueChart').replaceChildren(); $('#revenueChart').classList.remove('is-empty'); $('#chartDataBody').replaceChildren(); text('#trendStatus',error.message+' Chọn Tải lại để thử lại.'); } }
  finally { if(request===trendRequest) { $('#revenueChart').setAttribute('aria-busy','false'); $('#retryTrend').disabled=false; } }
}
$('#trendWindow').addEventListener('change',()=>{ state={...state,months:Number($('#trendWindow').value)}; saveURL(); drawTrend(); });
$('#retryTrend').addEventListener('click',loadTrend);
document.addEventListener('keydown',event=>{ if(event.key==='Escape') document.querySelectorAll('.month-bar').forEach(button=>button.classList.add('tooltip-dismissed')); });
window.addEventListener('popstate',()=>{
  if(lastSearch===location.search) return;
  try { state=readDashboardState(location.search); lastSearch=location.search; controls(); drawTrend(); loadPeriod('replace'); }
  catch { text('#filterStatus','Địa chỉ chứa bộ lọc không hợp lệ. Chọn Về tháng hiện tại để khôi phục.'); }
});
controls(); loadOverview(); loadPeriod('replace'); loadTrend();
