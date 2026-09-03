// API response envelope
export interface ApiResponse<T> {
  data: T;
  meta?: Record<string, unknown>;
  error?: { code: string; message: string };
}

// Organization
export interface Organization {
  id: string;
  name: string;
  slug: string;
  color: string;
  nusach: string | null;
  nusachDisplay: string | null;
  address: string | null;
  websiteUrl: string | null;
  whatsapp: string | null;
  latitude: number | null;
  longitude: number | null;
}

// Org summary embedded in schedule events
export interface OrgSummary {
  id: string;
  name: string;
  slug: string;
  color: string;
  whatsapp: string | null;
}

// A single materialized schedule event
export interface ScheduleEvent {
  id: string;
  date: string;           // "YYYY-MM-DD"
  startTime: string;      // "HH:mm"
  minyanType: string;     // "SHACHARIS" | "MINCHA" | "MAARIV" | etc.
  minyanTypeDisplay: string;
  displayMinyanType: string;
  displayMinyanTypeDisplay: string;
  groupMinyanType: string;
  groupMinyanTypeDisplay: string;
  linkedMinyanType: string | null;
  linkedMinyanTypeDisplay: string | null;
  linkedStartTime: string | null;
  linkedTarget: boolean;
  organization: OrgSummary;
  locationName: string | null;
  notes: string | null;
  nusach: string | null;
  nusachDisplay: string | null;
  dynamicTimeString: string | null;
  source: 'RULES' | 'IMPORTED' | 'MANUAL';
  whatsapp: string | null;
}

// Zmanim
export interface ZmanimTimes {
  alotHashachar: string | null;
  misheyakir: string | null;
  netz: string | null;
  sofZmanShmaGra: string | null;
  sofZmanShmaMga: string | null;
  sofZmanTfilaGra: string | null;
  sofZmanTfilaMga: string | null;
  chatzos: string | null;
  minchaGedola: string | null;
  minchaKetana: string | null;
  plagHamincha: string | null;
  shekiya: string | null;
  tzeis: string | null;
  earliestShema: string | null;
  candleLighting?: string | null;
  havdala?: string | null;
  chatzosLaila: string | null;
}

export interface Zmanim {
  date: string;
  hebrewDate: string;
  times: ZmanimTimes;
}

// Notification
export interface Notification {
  id: number;
  title: string;
  message: string;
  type: 'BANNER' | 'POPUP';
  expiresAt: string | null;
  maxDisplays: number | null;
}

export type FeedbackCategory = 'MINYAN_SCHEDULE' | 'APP_FUNCTIONALITY';

export interface FeedbackOrganizationContext {
  id?: string | null;
  slug?: string | null;
  name?: string | null;
}

export interface FeedbackMinyanContext {
  id?: string | null;
  type?: string | null;
  time?: string | null;
  date?: string | null;
  locationName?: string | null;
}

export interface FeedbackCalendarContext {
  eventId?: string | null;
  entryId?: string | null;
  source?: string | null;
  sourceId?: string | null;
}

export interface FeedbackPostHogContext {
  distinctId?: string | null;
  sessionId?: string | null;
  sessionReplayUrl?: string | null;
}

export interface FeedbackMetadata {
  platform: 'ios' | 'android' | 'mobile' | 'native';
  screen?: string | null;
  page?: string | null;
  route?: string | null;
  url?: string | null;
  appVersion?: string | null;
  buildNumber?: string | null;
  browser?: string | null;
  userAgent?: string | null;
  deviceModel?: string | null;
  osName?: string | null;
  osVersion?: string | null;
  selectedDate?: string | null;
  organization?: FeedbackOrganizationContext | null;
  minyan?: FeedbackMinyanContext | null;
  calendar?: FeedbackCalendarContext | null;
  posthog?: FeedbackPostHogContext | null;
  filters?: Record<string, string>;
  routeParams?: Record<string, string>;
  extra?: Record<string, string>;
}

export interface FeedbackSubmissionRequest {
  message: string;
  email?: string;
  category: FeedbackCategory;
  metadata: FeedbackMetadata;
}

export interface FeedbackSubmissionResponse {
  feedbackId: string;
  githubIssueNumber: number;
  githubIssueUrl: string;
  userEmailProvided: boolean;
  notificationEmailSent: boolean;
  notificationEmailMessage?: string | null;
}
