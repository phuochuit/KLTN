import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyPricingResponse, formatPricingValue, pricingValuesEqual } from '../../main/resources/static/js/pricing-model.mjs';

test('pricing confirms only the redirected server flash', () => {
  assert.equal(classifyPricingResponse({ status: 200, redirected: true, pathname: '/pricing', message: 'Đã cập nhật' }), 'success');
  assert.equal(classifyPricingResponse({ status: 200, redirected: true, pathname: '/pricing' }), 'unknown');
  assert.equal(classifyPricingResponse({ status: 200, pathname: '/pricing', message: 'Đã cập nhật' }), 'unknown');
  assert.equal(classifyPricingResponse({ status: 503, pathname: '/pricing' }), 'unknown');
  assert.equal(classifyPricingResponse({ status: 403, pathname: '/pricing' }), 'blocked');
});

test('pricing review formats Vietnamese grouped numbers and keeps units', () => {
  assert.equal(formatPricingValue('15000', 'đ'), '15.000 đ');
  assert.equal(formatPricingValue('2.5', 'giờ'), '2,5 giờ');
  assert.equal(formatPricingValue('2000.0000001', 'đ'), '2.000,0000001 đ');
  assert.equal(formatPricingValue('1e-7', 'đ'), '0,0000001 đ');
  assert.equal(formatPricingValue('1.25e+3', 'đ'), '1.250 đ');
});

test('pricing comparison uses exact decimal equivalence, including exponents and beyond Number precision', () => {
  assert.equal(pricingValuesEqual('2000', '2000.0'), true);
  assert.equal(pricingValuesEqual('1e3', '1000.0'), true);
  assert.equal(pricingValuesEqual('2000', '2000.01'), false);
  assert.equal(pricingValuesEqual('2000', '2000.0000000000001'), false);
  assert.equal(pricingValuesEqual('0', '0.00'), true);
  assert.equal(pricingValuesEqual('', '0'), false);
});
