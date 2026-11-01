import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyPricingResponse } from '../../main/resources/static/js/pricing-model.mjs';

test('pricing confirms only the redirected server flash', () => {
  assert.equal(classifyPricingResponse({ status: 200, redirected: true, pathname: '/pricing', message: 'Đã cập nhật' }), 'success');
  assert.equal(classifyPricingResponse({ status: 200, redirected: true, pathname: '/pricing' }), 'unknown');
  assert.equal(classifyPricingResponse({ status: 200, pathname: '/pricing', message: 'Đã cập nhật' }), 'unknown');
  assert.equal(classifyPricingResponse({ status: 503, pathname: '/pricing' }), 'unknown');
  assert.equal(classifyPricingResponse({ status: 403, pathname: '/pricing' }), 'blocked');
});
