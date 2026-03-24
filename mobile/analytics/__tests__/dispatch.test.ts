import { describe, expect, it, vi } from 'vitest';

import { dispatchCapture } from '../dispatch';

describe('dispatchCapture', () => {
  it('does not call capture when analytics is disabled', () => {
    const capture = vi.fn();

    const didCapture = dispatchCapture({ capture }, false, 'screen_view', {
      pathname: '/(tabs)',
    });

    expect(didCapture).toBe(false);
    expect(capture).not.toHaveBeenCalled();
  });

  it('calls capture when analytics is enabled', () => {
    const capture = vi.fn();

    const didCapture = dispatchCapture({ capture }, true, 'screen_view', {
      pathname: '/(tabs)',
    });

    expect(didCapture).toBe(true);
    expect(capture).toHaveBeenCalledTimes(1);
    expect(capture).toHaveBeenCalledWith('screen_view', { pathname: '/(tabs)' });
  });
});
