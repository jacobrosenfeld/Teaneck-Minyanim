type Callback = () => void;

let _scrollToNow: Callback | null = null;
let _goToday: Callback | null = null;

export function registerScrollToNow(fn: Callback) { _scrollToNow = fn; }
export function unregisterScrollToNow() { _scrollToNow = null; }
export function triggerScrollToNow() { _scrollToNow?.(); }

export function registerGoToday(fn: Callback) { _goToday = fn; }
export function unregisterGoToday() { _goToday = null; }
export function triggerGoToday() { _goToday?.(); }
