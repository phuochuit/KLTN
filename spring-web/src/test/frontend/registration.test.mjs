import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  MAX_IMAGE_BYTES,
  classifyRegistrationWrite,
  formatVietnameseDate,
  formatVietnameseDateInput,
  imageFileError,
  qrFieldValues,
  parseVietnameseDate,
} from '../../main/resources/static/js/registration-model.mjs';

describe('registration media, dates, and write feedback model', () => {
  it('accepts an image at the 10 MB limit and rejects invalid or oversized files', () => {
    assert.equal(imageFileError(null), null);
    assert.equal(imageFileError({ size: MAX_IMAGE_BYTES, type: 'image/png' }), null);
    assert.match(imageFileError({ size: MAX_IMAGE_BYTES + 1, type: 'image/png' }), /10 MB/);
    assert.match(imageFileError({ size: 10, type: 'text/plain' }), /hình ảnh/);
  });

  it('returns only reviewable CCCD fields and never exposes the raw QR payload as a field', () => {
    assert.deepEqual(qrFieldValues({
      raw: 'synthetic-raw-qr',
      citizenId: '012345678901',
      oldCitizenId: '',
      fullName: ' Nguyễn Văn Tổng Hợp ',
      dateOfBirth: '1990-02-03',
      gender: 'Nam',
      permanentAddress: 'Địa chỉ tổng hợp',
      cccdIssueDate: null,
    }), [
      { name: 'citizenId', label: 'Số CCCD', value: '012345678901' },
      { name: 'fullName', label: 'Họ và tên', value: 'Nguyễn Văn Tổng Hợp' },
      { name: 'dateOfBirth', label: 'Ngày sinh', value: '1990-02-03' },
      { name: 'gender', label: 'Giới tính', value: 'Nam' },
      { name: 'permanentAddress', label: 'Địa chỉ thường trú', value: 'Địa chỉ tổng hợp' },
    ]);
  });

  it('recognizes only the two approved success destinations after a redirect', () => {
    assert.equal(classifyRegistrationWrite({ status: 200, redirected: true, pathname: '/cards' }), 'success');
    assert.equal(classifyRegistrationWrite({ status: 200, redirected: true, pathname: '/vehicles' }), 'success');
    assert.equal(classifyRegistrationWrite({ status: 200, redirected: true, pathname: '/login' }), 'session');
    assert.equal(classifyRegistrationWrite({ status: 200, redirected: false, pathname: '/registrations' }), 'unknown');
  });

  it('keeps known validation/conflict/upload errors separate from unknown write outcomes', () => {
    assert.equal(classifyRegistrationWrite({ status: 400, redirected: false, pathname: '/registrations' }), 'validation-error');
    assert.equal(classifyRegistrationWrite({ status: 409, redirected: false, pathname: '/registrations' }), 'conflict');
    assert.equal(classifyRegistrationWrite({ status: 413, redirected: false, pathname: '/registrations' }), 'upload-error');
    assert.equal(classifyRegistrationWrite({ status: 403, redirected: false, pathname: '/registrations' }), 'blocked');
    for (const status of [0, 503, 500]) {
      assert.equal(classifyRegistrationWrite({ status, redirected: false, pathname: '/registrations' }), 'unknown');
    }
  });

  it('formats and parses strict Vietnamese calendar dates without changing ISO payload values', () => {
    assert.equal(formatVietnameseDate('1990-02-01'), '01/02/1990');
    assert.equal(formatVietnameseDate('2024-02-29'), '29/02/2024');
    assert.equal(formatVietnameseDate('2024-02-30'), '');
    assert.equal(parseVietnameseDate('01/02/1990'), '1990-02-01');
    assert.equal(parseVietnameseDate('29/02/2024'), '2024-02-29');
    assert.equal(parseVietnameseDate('29/02/2023'), null);
    assert.equal(parseVietnameseDate('31/04/2024'), null);
    assert.equal(parseVietnameseDate('1/2/1990'), null);
  });

  it('formats numeric date entry as dd/MM/yyyy while keeping partial input editable', () => {
    assert.equal(formatVietnameseDateInput('02011990'), '02/01/1990');
    assert.equal(formatVietnameseDateInput('02/01/1990'), '02/01/1990');
    assert.equal(formatVietnameseDateInput('020'), '02/0');
    assert.equal(formatVietnameseDateInput('020119900'), '02/01/1990');
  });
});
