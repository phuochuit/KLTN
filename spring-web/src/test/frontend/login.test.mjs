import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const script = readFileSync(new URL('../../main/resources/static/js/login.js', import.meta.url), 'utf8');
function setup() {
  const listeners = new Map();
  const elements = Object.fromEntries(['loginForm', 'password', 'togglePassword', 'passwordIcon', 'loginSubmit', 'submitLabel', 'submitStatus'].map(id => [id, {
    type: id === 'password' ? 'password' : '', hidden: true, disabled: false,
    dataset: { eye: '/eye.svg', eyeOff: '/eye-off.svg' }, attributes: {},
    addEventListener(type, handler) { listeners.set(`${id}:${type}`, handler); },
    setAttribute(name, value) { this.attributes[name] = value; },
    removeAttribute(name) { delete this.attributes[name]; },
  }]));
  vm.runInNewContext(script, {
    document: { getElementById: id => elements[id] },
    window: { addEventListener: (type, handler) => listeners.set(type, handler) },
  });
  return { elements, listeners };
}

test('visibility button exposes current state without editing credentials', () => {
  const { elements: e, listeners } = setup();
  assert.equal(e.togglePassword.hidden, false);
  listeners.get('togglePassword:click')();
  assert.equal(e.password.type, 'text');
  assert.equal(e.togglePassword.attributes['aria-pressed'], 'true');
  assert.equal(e.togglePassword.attributes['aria-label'], 'Ẩn mật khẩu');
  assert.equal(e.passwordIcon.src, '/eye-off.svg');
  listeners.get('togglePassword:click')();
  assert.equal(e.password.type, 'password');
});

test('first submit remains native and credentials stay enabled; repeated submit is prevented', () => {
  const { elements: e, listeners } = setup();
  let prevented = 0;
  const event = { preventDefault() { prevented++; } };
  listeners.get('loginForm:submit')(event);
  assert.equal(prevented, 0);
  assert.equal(e.loginSubmit.disabled, true);
  assert.equal(e.password.disabled, false);
  assert.equal(e.loginForm.attributes['aria-busy'], 'true');
  listeners.get('loginForm:submit')(event);
  assert.equal(prevented, 1);
});

test('history restore clears busy state and masks password again', () => {
  const { elements: e, listeners } = setup();
  listeners.get('togglePassword:click')();
  listeners.get('loginForm:submit')({ preventDefault() {} });
  listeners.get('pageshow')();
  assert.equal(e.loginSubmit.disabled, false);
  assert.equal(e.loginForm.attributes['aria-busy'], undefined);
  assert.equal(e.password.type, 'password');
  assert.equal(e.submitStatus.textContent, '');
  let prevented = false;
  listeners.get('loginForm:submit')({ preventDefault() { prevented = true; } });
  assert.equal(prevented, false);
});
