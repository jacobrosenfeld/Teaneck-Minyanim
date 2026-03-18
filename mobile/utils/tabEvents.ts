type Callback = () => void;

let _scrollToNow: Callback | null = null;
let _goToday: Callback | null = null;

export function registerScrollToNow(fn: Callback) { _scrollToNow = fn; }
export function unregisterScrollToNow() { _scrollToNow = null; }
export function triggerScrollToNow() { _scrollToNow?.(); }

export function registerGoToday(fn: Callback) { _goToday = fn; }
export function unregisterGoToday() { _goToday = null; }
export function triggerGoToday() { _goToday?.(); }

// Open the ShulDaySheet from a notification tap
export interface SheetTarget {
  eventId: string;
  date: string;
  orgSlug: string;
  orgName: string;
}

let _openSheet: ((target: SheetTarget) => void) | null = null;

export function registerOpenSheet(fn: (target: SheetTarget) => void) { _openSheet = fn; }
export function unregisterOpenSheet() { _openSheet = null; }
export function triggerOpenSheet(target: SheetTarget) { _openSheet?.(target); }
