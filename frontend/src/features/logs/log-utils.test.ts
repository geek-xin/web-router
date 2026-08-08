import { describe, expect, it } from 'vitest';
import { addLogEntry, buildPathDurationStats, snapshotToState, updateDurationTopLogs } from './log-utils';

describe('log-utils', () => {
  it('aggregates total duration by normalized path', () => {
    expect(buildPathDurationStats([{ path: '', durationMs: 5 }, { path: '/api', durationMs: 7 }])).toEqual({ '/': 5, '/api': 7 });
  });

  it('keeps duration top logs sorted descending and capped', () => {
    const logs = updateDurationTopLogs([{ path: '/a', durationMs: 1 }], { path: '/b', durationMs: 10 }, 1);
    expect(logs).toEqual([{ path: '/b', durationMs: 10 }]);
  });

  it('uses full-scope failed and slow counters from snapshot', () => {
    const state = snapshotToState({
      totalRequests: 500,
      failedRequests: 23,
      slowRequests: 7,
      totalDurationMs: 0,
      recentLogs: [],
    });
    expect(state.totalRequests).toBe(500);
    expect(state.failedRequests).toBe(23);
    expect(state.slowRequests).toBe(7);
  });

  it('increments failed and slow counters per entry', () => {
    let state = snapshotToState({
      totalRequests: 0,
      failedRequests: 0,
      slowRequests: 0,
      totalDurationMs: 0,
      recentLogs: [],
    });
    state = addLogEntry(state, { path: '/ok', status: 200, durationMs: 50 });
    state = addLogEntry(state, { path: '/err', status: 500, durationMs: 80 });
    state = addLogEntry(state, { path: '/slow', status: 200, durationMs: 1200 });
    expect(state.totalRequests).toBe(3);
    expect(state.failedRequests).toBe(1);
    expect(state.slowRequests).toBe(1);
  });
});
