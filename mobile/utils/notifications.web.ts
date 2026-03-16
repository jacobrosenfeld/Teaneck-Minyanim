// Web stub — expo-notifications does not support web (localStorage crash during SSR)
export const REMINDER_CATEGORY = 'minyan-reminder';
export const SNOOZE_ACTION = 'SNOOZE_5';

export interface ReminderOptions {
  eventId: string;
  orgName: string;
  minyanType: string;
  startTime: string;
  date: string;
  minutesBefore?: number;
}

export async function requestNotificationPermission(): Promise<boolean> { return false; }
export async function scheduleReminder(_opts: ReminderOptions): Promise<string | null> { return null; }
export async function cancelReminder(_eventId: string): Promise<void> {}
export async function isReminderSet(_eventId: string): Promise<boolean> { return false; }
