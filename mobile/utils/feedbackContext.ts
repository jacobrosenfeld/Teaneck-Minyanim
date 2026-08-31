import type { Organization } from '@/api/types';

export interface FeedbackNavigationContext {
  pathname: string;
  screen: string;
  routeParams: Record<string, string>;
  capturedAt: string;
}

const DEFAULT_CONTEXT: FeedbackNavigationContext = {
  pathname: '/',
  screen: 'minyanim',
  routeParams: {},
  capturedAt: new Date(0).toISOString(),
};

let latestContext = DEFAULT_CONTEXT;

export function setFeedbackNavigationContext(
  pathname: string,
  routeParams: Record<string, string>,
): void {
  if (!pathname || pathname.includes('feedback')) {
    return;
  }

  latestContext = {
    pathname,
    routeParams: cleanRouteParams(routeParams),
    screen: screenForPathname(pathname),
    capturedAt: new Date().toISOString(),
  };
}

export function getFeedbackNavigationContext(): FeedbackNavigationContext {
  return latestContext;
}

export function findOrganizationForFeedbackContext(
  context: FeedbackNavigationContext,
  organizations: Organization[] | undefined,
): Organization | null {
  if (!organizations || organizations.length === 0) {
    return null;
  }

  const candidate = context.routeParams.id
    ?? context.routeParams.orgId
    ?? context.routeParams.organizationId
    ?? context.routeParams.orgSlug;
  if (!candidate) {
    return null;
  }

  return organizations.find((org) => org.id === candidate || org.slug === candidate) ?? null;
}

function screenForPathname(pathname: string): string {
  if (pathname.includes('/map')) return 'map';
  if (pathname.includes('/zmanim')) return 'zmanim';
  if (pathname.includes('/shuls/') || pathname.includes('/shul/')) return 'organization-detail';
  if (pathname.includes('/shuls')) return 'organizations-list';
  return 'minyanim';
}

function cleanRouteParams(routeParams: Record<string, string>): Record<string, string> {
  return Object.entries(routeParams).reduce<Record<string, string>>((cleaned, [key, value]) => {
    const normalizedKey = key.trim();
    const normalizedValue = value.trim();
    if (normalizedKey && normalizedValue) {
      cleaned[normalizedKey] = normalizedValue.slice(0, 200);
    }
    return cleaned;
  }, {});
}
