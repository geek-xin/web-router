import { describe, expect, it } from 'vitest';
import { buildPathDurationStats, updateDurationTopLogs } from './log-utils';

describe('log-utils', () => {
  it('aggregates total duration by normalized path', () => {
    expect(buildPathDurationStats([{ path: '', durationMs: 5 }, { path: '/api', durationMs: 7 }])).toEqual({ '/': 5, '/api': 7 });
  });

  it('keeps duration top logs sorted descending and capped', () => {
    const logs = updateDurationTopLogs([{ path: '/a', durationMs: 1 }], { path: '/b', durationMs: 10 }, 1);
    expect(logs).toEqual([{ path: '/b', durationMs: 10 }]);
  });
});
