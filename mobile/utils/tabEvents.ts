type Callback = () => void;

let _scrollToNow: Callback | null = null;

export function registerScrollToNow(fn: Callback) { _scrollToNow = fn; }
export function unregisterScrollToNow() { _scrollToNow = null; }
export function triggerScrollToNow() { _scrollToNow?.(); }
