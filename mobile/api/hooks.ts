import { useQuery } from '@tanstack/react-query';
import {
  fetchOrganizations,
  fetchOrganization,
  fetchSchedule,
  fetchOrgSchedule,
  fetchZmanim,
  fetchNotifications,
  toApiDate,
  type ScheduleParams,
} from './client';

// --- Organizations ---

export function useOrganizations() {
  return useQuery({
    queryKey: ['organizations'],
    queryFn: fetchOrganizations,
    staleTime: 5 * 60 * 1000, // 5 min
  });
}

export function useOrganization(idOrSlug: string) {
  return useQuery({
    queryKey: ['organization', idOrSlug],
    queryFn: () => fetchOrganization(idOrSlug),
    staleTime: 5 * 60 * 1000,
    enabled: !!idOrSlug,
  });
}

// --- Schedule ---

export function useTodaySchedule() {
  const today = toApiDate(new Date());
  return useQuery({
    queryKey: ['schedule', 'today', today],
    queryFn: () => fetchSchedule({ date: today }),
    staleTime: 2 * 60 * 1000, // 2 min
  });
}

export function useSchedule(params: ScheduleParams) {
  return useQuery({
    queryKey: ['schedule', params],
    queryFn: () => fetchSchedule(params),
    staleTime: 2 * 60 * 1000,
    enabled: !!(params.date || (params.start && params.end)),
  });
}

export function useOrgSchedule(idOrSlug: string, params: ScheduleParams) {
  return useQuery({
    queryKey: ['schedule', 'org', idOrSlug, params],
    queryFn: () => fetchOrgSchedule(idOrSlug, params),
    staleTime: 2 * 60 * 1000,
    enabled: !!idOrSlug && !!(params.date || (params.start && params.end)),
  });
}

// --- Zmanim ---

export function useZmanim(date?: string) {
  const today = toApiDate(new Date());
  return useQuery({
    queryKey: ['zmanim', date ?? today],
    queryFn: () => fetchZmanim(date),
    staleTime: 60 * 60 * 1000, // 1 hour — zmanim don't change mid-day
  });
}

// --- Notifications ---

export function useNotifications(type?: 'BANNER' | 'POPUP') {
  return useQuery({
    queryKey: ['notifications', type],
    queryFn: () => fetchNotifications(type),
    staleTime: 5 * 60 * 1000,
  });
}
