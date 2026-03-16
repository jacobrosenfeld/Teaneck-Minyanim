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
