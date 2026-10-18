import assert from 'node:assert/strict';
import { dueState, dueLabel } from '../frontend/src/deadlines.ts';
const now=Date.parse('2026-10-18T00:00:00Z');
assert.equal(dueState('2026-10-17T23:59:59Z',now),'CLOSED');assert.equal(dueState('2026-10-18T00:00:00Z',now),'CLOSED');assert.equal(dueState('2026-10-18T00:00:01Z',now),'DUE_SOON');assert.equal(dueState('2026-10-19T00:00:00Z',now),'DUE_SOON');assert.equal(dueState('2026-10-19T00:00:01Z',now),'OPEN');assert.equal(dueLabel('CLOSED'),'已截止');console.log('Deadline boundaries: 6 assertions passed.');
