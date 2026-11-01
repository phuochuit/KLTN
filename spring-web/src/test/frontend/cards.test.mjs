import test from 'node:test';
import assert from 'node:assert/strict';
import {
  classifyCardWrite,
  cardSuccessMessage,
  formatVietnameseDate,
  inclusiveEndDate,
  parseVietnameseDate,
} from '../../main/resources/static/js/cards-model.mjs';

test('Vietnamese card dates parse and format only real calendar days', () => {
  assert.equal(formatVietnameseDate('2026-11-01'), '01/11/2026');
  assert.equal(parseVietnameseDate('29/02/2028'), '2028-02-29');
  assert.equal(parseVietnameseDate('29/02/2027'), null);
  assert.equal(parseVietnameseDate('1/11/2026'), null);
  assert.equal(formatVietnameseDate('2026-02-30'), '');
});

test('success feedback requires the server flash from the redirected cards document', () => {
  const documentWithFlash = { querySelector: selector => selector === '.cards-feedback-success p'
    ? { textContent: '  Đã lưu thẻ vé lượt  ' } : null };
  assert.equal(cardSuccessMessage(documentWithFlash), 'Đã lưu thẻ vé lượt');
  assert.equal(cardSuccessMessage({ querySelector: () => null }), null);
  assert.equal(cardSuccessMessage({ querySelector: () => ({ textContent: '  ' }) }), null);
});

test('monthly period end is inclusive and stays calendar-based across leap day', () => {
  assert.equal(inclusiveEndDate('2026-11-01', 30), '2026-11-30');
  assert.equal(inclusiveEndDate('2028-02-01', 30), '2028-03-01');
  assert.equal(inclusiveEndDate('2028-02-28', 2), '2028-02-29');
  assert.equal(inclusiveEndDate('2026-11-01', 0), null);
  assert.equal(inclusiveEndDate('2026-11-01', 367), null);
});

test('only a successful server redirect back to cards confirms a write', () => {
  assert.equal(classifyCardWrite({ status: 200, redirected: true, pathname: '/cards' }), 'success');
  assert.equal(classifyCardWrite({ status: 400, pathname: '/cards' }), 'retry-safe');
  assert.equal(classifyCardWrite({ status: 409, pathname: '/cards' }), 'retry-safe');
  assert.equal(classifyCardWrite({ status: 413, pathname: '/cards' }), 'retry-safe');
  assert.equal(classifyCardWrite({ status: 503, pathname: '/cards' }), 'unknown');
  assert.equal(classifyCardWrite({ status: 0 }), 'unknown');
  assert.equal(classifyCardWrite({ status: 403 }), 'blocked');
  assert.equal(classifyCardWrite({ status: 200, redirected: true, pathname: '/login' }), 'session');
  assert.equal(classifyCardWrite({ status: 200, pathname: '/cards' }), 'unknown');
});
