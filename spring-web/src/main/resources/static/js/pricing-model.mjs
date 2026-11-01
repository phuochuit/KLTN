export function classifyPricingResponse({ status, redirected, pathname, message } = {}) {
  if ([401, 403].includes(status) || (redirected && /\/login\/?$/.test(pathname || ''))) return 'blocked';
  if (status >= 200 && status < 300 && redirected && /\/pricing\/?$/.test(pathname || '') && message) return 'success';
  return 'unknown';
}
