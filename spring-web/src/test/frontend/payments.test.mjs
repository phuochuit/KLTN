import test from 'node:test';
import assert from 'node:assert/strict';
import { matchesPaymentFilter, hasReconciliationNote, classifyPaymentResponse } from '../../main/resources/static/js/payments-model.mjs';

test('payment filter applies only the text and status supplied by loaded rows', () => {
  assert.equal(matchesPaymentFilter('PT-TEST-01 Chuyển khoản', 'PAID', 'test-01', 'PAID'), true);
  assert.equal(matchesPaymentFilter('PT-TEST-01 Chuyển khoản', 'RECONCILED', 'test-01', 'PAID'), false);
  assert.equal(matchesPaymentFilter('PT-TEST-01 Chuyển khoản', 'PAID', 'other', ''), false);
});

test('payment response confirms only server flash after payments redirect', () => {
  assert.equal(classifyPaymentResponse({status: 200, redirected: true, pathname: '/payments', message: 'Đã chốt'}), 'success');
  assert.equal(classifyPaymentResponse({status: 200, redirected: true, pathname: '/payments'}), 'unknown');
  assert.equal(classifyPaymentResponse({status: 503, pathname: '/payments'}), 'unknown');
  assert.equal(classifyPaymentResponse({status: 403}), 'blocked');
});

test('reconciliation note must contain non-whitespace text, matching server normalization', () => {
  assert.equal(hasReconciliationNote(''), false);
  assert.equal(hasReconciliationNote('   '), false);
  assert.equal(hasReconciliationNote('\t\n'), false);
  assert.equal(hasReconciliationNote('  Đã đối chiếu  '), true);
});
