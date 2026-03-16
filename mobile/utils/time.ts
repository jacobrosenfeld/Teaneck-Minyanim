/**
 * Convert "HH:mm" (24-hour) to "h:mm AM/PM" (12-hour).
 * e.g. "07:30" → "7:30 AM", "19:00" → "7:00 PM"
 */
export function formatTime(time: string): string {
  const [h, m] = time.split(':').map(Number);
  const period = h >= 12 ? 'PM' : 'AM';
  const hour = h % 12 || 12;
  return `${hour}:${m.toString().padStart(2, '0')} ${period}`;
}

/**
 * Given a map of label → "HH:mm" times, return the next upcoming one
 * relative to the current moment.
 */
export function getNextZman(
  entries: { label: string; key: string; time: string | null | undefined }[],
): { label: string; time: string } | null {
  const now = new Date();
  const nowMins = now.getHours() * 60 + now.getMinutes();

  for (const e of entries) {
    if (!e.time) continue;
    const [h, m] = e.time.split(':').map(Number);
    if (h * 60 + m > nowMins) return { label: e.label, time: e.time };
  }
  return null;
}
