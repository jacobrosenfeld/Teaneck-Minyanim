import axios from 'axios';
import { format } from 'date-fns';
import type {
  ApiResponse,
  FeedbackSubmissionRequest,
  FeedbackSubmissionResponse,
  Organization,
  ScheduleEvent,
  Zmanim,
  Notification,
} from './types';
import {
  MOBILE_FEEDBACK_APP_TOKEN_HEADER,
  mobileFeedbackHeaders,
} from './feedbackAuth';

// Point at production; override for local dev in .env.local
const BASE_URL =
  process.env.EXPO_PUBLIC_API_URL ?? 'https://teaneckminyanim.com';

const http = axios.create({
  baseURL: `${BASE_URL}/api/v1`,
  timeout: 10_000,
  headers: { Accept: 'application/json' },
});

// Helper: unwrap the ApiResponse envelope
function unwrap<T>(res: { data: ApiResponse<T> }): T {
  if (res.data.error) {
    throw new Error(res.data.error.message);
  }
  return res.data.data;
}

function apiErrorMessage(error: unknown): string | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }
  const data = error.response?.data as ApiResponse<unknown> | undefined;
  return data?.error?.message ?? null;
}

// --- Organizations ---

export async function fetchOrganizations(): Promise<Organization[]> {
  return unwrap(await http.get<ApiResponse<Organization[]>>('/organizations'));
}

export async function fetchOrganization(idOrSlug: string): Promise<Organization> {
  return unwrap(await http.get<ApiResponse<Organization>>(`/organizations/${idOrSlug}`));
}

// --- Schedule ---

export interface ScheduleParams {
  date?: string;     // single day: "YYYY-MM-DD"
  start?: string;
  end?: string;
}

export async function fetchSchedule(params: ScheduleParams): Promise<ScheduleEvent[]> {
  return unwrap(await http.get<ApiResponse<ScheduleEvent[]>>('/schedule', { params }));
}

export async function fetchOrgSchedule(
  idOrSlug: string,
  params: ScheduleParams,
): Promise<ScheduleEvent[]> {
  return unwrap(
    await http.get<ApiResponse<ScheduleEvent[]>>(
      `/organizations/${idOrSlug}/schedule`,
      { params },
    ),
  );
}

// --- Zmanim ---

export async function fetchZmanim(date?: string): Promise<Zmanim> {
  const params = date ? { date } : undefined;
  return unwrap(await http.get<ApiResponse<Zmanim>>('/zmanim', { params }));
}

// --- Notifications ---

export async function fetchNotifications(
  type?: 'BANNER' | 'POPUP',
): Promise<Notification[]> {
  return unwrap(
    await http.get<ApiResponse<Notification[]>>('/notifications', {
      params: type ? { type } : undefined,
    }),
  );
}

// --- Feedback ---

export async function submitFeedback(
  request: FeedbackSubmissionRequest,
): Promise<FeedbackSubmissionResponse> {
  const headers = mobileFeedbackHeaders();
  if (!headers[MOBILE_FEEDBACK_APP_TOKEN_HEADER]) {
    throw new Error('Mobile feedback is not configured.');
  }

  try {
    return unwrap(
      await http.post<ApiResponse<FeedbackSubmissionResponse>>('/feedback', request, {
        headers,
      }),
    );
  } catch (error) {
    const message = apiErrorMessage(error);
    if (message) {
      throw new Error(message);
    }
    throw error;
  }
}

export { isMobileFeedbackConfigured } from './feedbackAuth';

// Utility: format a date for API calls
export function toApiDate(d: Date): string {
  return format(d, 'yyyy-MM-dd');
}
