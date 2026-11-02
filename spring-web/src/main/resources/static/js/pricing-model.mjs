export function classifyPricingResponse({ status, redirected, pathname, message } = {}) {
  if ([401, 403].includes(status) || (redirected && /\/login\/?$/.test(pathname || ''))) return 'blocked';
  if (status >= 200 && status < 300 && redirected && /\/pricing\/?$/.test(pathname || '') && message) return 'success';
  return 'unknown';
}

export function formatPricingValue(value, unit) {
  const decimal = parsePricingDecimal(value);
  if (!decimal) return `${String(value)} ${unit}`;
  if (decimal.scale > 1000n || decimal.scale < -1000n) {
    return `${decimal.source.replace('.', ',')} ${unit}`;
  }

  const scale = Number(decimal.scale);
  let integer;
  let fraction;
  if (scale <= 0) {
    integer = decimal.digits + '0'.repeat(-scale);
    fraction = '';
  } else {
    const integerLength = decimal.digits.length - scale;
    if (integerLength <= 0) {
      integer = '0';
      fraction = '0'.repeat(-integerLength) + decimal.digits;
    } else {
      integer = decimal.digits.slice(0, integerLength);
      fraction = decimal.digits.slice(integerLength);
    }
  }

  integer = integer.replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const sign = decimal.negative && /[1-9]/.test(decimal.digits) ? '-' : '';
  return `${sign}${integer}${fraction ? `,${fraction}` : ''} ${unit}`;
}

export function pricingValuesEqual(left, right) {
  const leftDecimal = parsePricingDecimal(left);
  const rightDecimal = parsePricingDecimal(right);
  if (!leftDecimal || !rightDecimal) return String(left) === String(right);
  const leftCanonical = canonicalPricingDecimal(leftDecimal);
  const rightCanonical = canonicalPricingDecimal(rightDecimal);
  return leftCanonical.negative === rightCanonical.negative
    && leftCanonical.digits === rightCanonical.digits
    && leftCanonical.scale === rightCanonical.scale;
}

function parsePricingDecimal(value) {
  const source = String(value).trim();
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(source);
  if (!match) return null;
  const integer = match[2] ?? '0';
  const fraction = match[3] ?? match[4] ?? '';
  return {
    source,
    negative: match[1] === '-',
    digits: integer + fraction,
    scale: BigInt(fraction.length) - BigInt(match[5] ?? '0'),
  };
}

function canonicalPricingDecimal(decimal) {
  let digits = decimal.digits.replace(/^0+/, '') || '0';
  if (digits === '0') return { negative: false, digits, scale: 0n };
  let scale = decimal.scale;
  while (digits.length > 1 && digits.endsWith('0')) {
    digits = digits.slice(0, -1);
    scale -= 1n;
  }
  return { negative: decimal.negative && digits !== '0', digits, scale };
}
