export const money = value => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value) + ' đ';
export const localDate = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
export function revenueParts(data) {
  const values = [data.visitRevenue, data.subscriptionRevenue, data.totalRevenue];
  if (values.some(value => value === null || value === '' || !['number', 'string'].includes(typeof value) || !Number.isFinite(Number(value)) || Number(value) < 0)) throw new Error('Dữ liệu doanh thu không hợp lệ.');
  const [visit, subscription, total] = values.map(Number);
  if (Math.abs(visit + subscription - total) > 0.01) throw new Error('Tổng doanh thu chưa khớp với các nguồn thu.');
  return { visit, subscription, total, visitPercent: total ? visit / total * 100 : 0 };
}
export function periodQuery({ type, year, month, from, to }) {
  const query = new URLSearchParams({ type });
  if (type === 'month' || type === 'year') {
    if (!/^\d{4}$/.test(year) || Number(year) < 1900 || Number(year) > 9998) throw new Error('Năm không hợp lệ.');
    query.set('year', year);
    if (type === 'month') {
      if (!/^\d{1,2}$/.test(month) || Number(month) < 1 || Number(month) > 12) throw new Error('Tháng không hợp lệ.');
      query.set('month', month);
    }
  } else if (type === 'range') {
    const valid = value => /^\d{4}-\d{2}-\d{2}$/.test(value ?? '') && Number(value.slice(0,4)) >= 1900 && Number(value.slice(0,4)) <= 9998 && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0,10) === value;
    if (!valid(from) || !valid(to) || from > to) throw new Error('Chọn khoảng ngày hợp lệ: ngày bắt đầu không sau ngày kết thúc.');
    query.set('from', from); query.set('to', to);
  } else throw new Error('Chọn loại thống kê hợp lệ.');
  return query;
}
export function readDashboardState(search = '', now = new Date(), defaultMonths = 12) {
  const query = new URLSearchParams(search);
  const state = {
    type: query.get('type') ?? 'month', year: query.get('year') ?? String(now.getFullYear()), month: query.get('month') ?? String(now.getMonth() + 1),
    from: query.get('from') ?? localDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: query.get('to') ?? localDate(now),
    q: (query.get('q') ?? '').trim(), source: query.get('source') ?? 'ALL', sort: query.get('sort') ?? 'newest',
    page: Number(query.get('page') ?? 0), size: 10, months: Number(query.get('months') ?? defaultMonths),
  };
  periodQuery(state);
  if (!Number.isSafeInteger(state.page) || state.page < 0 || state.page > 1000000 || ![6,12].includes(state.months) || state.q.length > 80 || !['ALL','VISIT','SUBSCRIPTION'].includes(state.source) || !['newest','oldest'].includes(state.sort)) throw new Error('Bộ lọc trong địa chỉ không hợp lệ.');
  return state;
}
export function dashboardSearch(state) {
  // Keep draft-independent period fields as well: back/forward must restore the complete control state.
  return new URLSearchParams(Object.fromEntries(Object.entries(state).filter(([key]) => key !== 'size').map(([key,value]) => [key,String(value)]))).toString();
}
