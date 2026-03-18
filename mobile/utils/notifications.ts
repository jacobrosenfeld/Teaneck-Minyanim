import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

// Show notifications when app is foregrounded
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export const REMINDER_CATEGORY = 'minyan-reminder';
export const SNOOZE_ACTION = 'SNOOZE_5';

export async function requestNotificationPermission(): Promise<boolean> {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('minyan-reminders', {
      name: 'Minyan Reminders',
      importance: Notifications.AndroidImportance.HIGH,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#275ed8',
    });
  }

  const { status: existing } = await Notifications.getPermissionsAsync();
  if (existing === 'granted') {
    await setupReminderCategory();
    return true;
  }

  const { status } = await Notifications.requestPermissionsAsync();
  if (status === 'granted') await setupReminderCategory();
  return status === 'granted';
}

async function setupReminderCategory() {
  await Notifications.setNotificationCategoryAsync(REMINDER_CATEGORY, [
    {
      identifier: SNOOZE_ACTION,
      buttonTitle: 'Snooze 5 min',
      options: { opensAppToForeground: false },
    },
  ]);
}

export interface ReminderOptions {
  eventId: string;         // used as the notification identifier for deduplication
  orgName: string;
  orgSlug: string;
  minyanType: string;
  startTime: string;       // "HH:mm"
  date: string;            // "YYYY-MM-DD"
  minutesBefore?: number;  // default 10
}

/**
 * Schedules a local notification for a minyan. Returns the notification ID,
 * or null if permission was denied or the time is already past.
 */
export async function scheduleReminder(opts: ReminderOptions): Promise<string | null> {
  const granted = await requestNotificationPermission();
  if (!granted) return null;

  const minutesBefore = opts.minutesBefore ?? 10;

  const [year, month, day] = opts.date.split('-').map(Number);
  const [hour, minute] = opts.startTime.split(':').map(Number);

  const minyanDate = new Date(year, month - 1, day, hour, minute, 0);
  const triggerDate = new Date(minyanDate.getTime() - minutesBefore * 60 * 1000);

  if (triggerDate <= new Date()) return null;

  // Cancel any existing reminder for this event first
  await cancelReminder(opts.eventId);

  const id = await Notifications.scheduleNotificationAsync({
    identifier: opts.eventId,
    content: {
      title: `${opts.minyanType} in ${minutesBefore} minutes`,
      body: `${opts.orgName} · ${formatDisplayTime(opts.startTime)}`,
      sound: true,
      categoryIdentifier: REMINDER_CATEGORY,
      data: {
        eventId: opts.eventId,
        date: opts.date,
        minyanType: opts.minyanType,
        orgSlug: opts.orgSlug,
        orgName: opts.orgName,
        // originalTitle kept for backwards compat with any previously scheduled notifications
        originalTitle: `${opts.minyanType} in ${minutesBefore} minutes`,
        originalBody: `${opts.orgName} · ${formatDisplayTime(opts.startTime)}`,
      },
    },
    trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: triggerDate },
  });

  return id;
}

export async function cancelReminder(eventId: string): Promise<void> {
  await Notifications.cancelScheduledNotificationAsync(eventId);
}

export async function isReminderSet(eventId: string): Promise<boolean> {
  const scheduled = await Notifications.getAllScheduledNotificationsAsync();
  return scheduled.some((n) => n.identifier === eventId);
}

function formatDisplayTime(time: string): string {
  const [h, m] = time.split(':').map(Number);
  const ampm = h >= 12 ? 'PM' : 'AM';
  const hour = h % 12 || 12;
  return `${hour}:${m.toString().padStart(2, '0')} ${ampm}`;
}
