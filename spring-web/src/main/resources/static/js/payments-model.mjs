export function matchesPaymentFilter(text, status, query, selectedStatus) {
  return (!selectedStatus || status === selectedStatus) && String(text).toLocaleLowerCase('vi').includes(String(query).trim().toLocaleLowerCase('vi'));
}

export function classifyPaymentResponse({status, redirected, pathname, message} = {}) {
  if ([401, 403].includes(status) || (redirected && /\/login\/?$/.test(pathname || ''))) return 'blocked';
  if (status >= 200 && status < 300 && redirected && /\/payments\/?$/.test(pathname || '') && message) return 'success';
  return 'unknown';
}
