import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateUploadSizes, MAX_PHOTO_BYTES, MAX_UPLOAD_BYTES } from '../src/api/uploadLimits.js';
const file = size => ({ name: '사진.jpg', size });
test('accepts photos larger than the old 10MB limit up to exactly 30MB', () => {
  assert.equal(validateUploadSizes([file(11 * 1024 * 1024)]), null);
  assert.equal(validateUploadSizes([file(MAX_PHOTO_BYTES)]), null);
});
test('rejects an oversized photo with its filename', () => {
  assert.match(validateUploadSizes([file(MAX_PHOTO_BYTES + 1)]), /사진.jpg.*30MB/);
});
test('rejects oversized batch even when each photo fits', () => {
  assert.match(validateUploadSizes(Array.from({length:4},()=>file(MAX_PHOTO_BYTES))), /100MB/);
  assert.match(validateUploadSizes(Array.from({length:5},()=>file(MAX_UPLOAD_BYTES/5))), /100MB/);
});
