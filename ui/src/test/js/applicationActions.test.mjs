import { test, describe } from 'node:test';
import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const modPath = join(__dirname, '../../main/resources/static/applicationActions.js');
const code = readFileSync(modPath, 'utf-8');
global.window = global;
eval(code);

const { section, actions, ELIGIBLE_STATUS } = global.applicationActions;

describe('applicationActions', () => {
  test('returns empty section for ineligible statuses', () => {
    ['DRAFT','GENERATED','UNDER_REVIEW','REJECTED','ARCHIVED','EMAIL_SENT'].forEach(s => {
      assert.equal(section({ id: 1, applicationStatus: s }), '');
      assert.equal(actions({ id: 1, applicationStatus: s }).length, 0);
    });
  });
  test('returns two actions only for APPROVED', () => {
    const a = { id: 7, applicationStatus: ELIGIBLE_STATUS };
    const list = actions(a);
    assert.equal(list.length, 2);
    assert.equal(list[0].key, 'email');
    assert.equal(list[1].key, 'apply');
    const s = section(a);
    assert.ok(s.includes('data-review-email'));
    assert.ok(s.includes('data-review-apply'));
    assert.ok(s.includes('Send or apply'));
  });
  test('section empty if id missing', () => {
    assert.equal(section({ applicationStatus: ELIGIBLE_STATUS }), '');
  });
});

