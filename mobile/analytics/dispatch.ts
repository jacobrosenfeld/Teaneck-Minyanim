export interface CaptureClient<TProperties> {
  capture: (eventName: string, properties?: TProperties) => void;
}

export function dispatchCapture<TProperties>(
  client: CaptureClient<TProperties> | null,
  analyticsEnabled: boolean,
  eventName: string,
  properties: TProperties,
): boolean {
  if (!analyticsEnabled || !client) {
    return false;
  }

  client.capture(eventName, properties);
  return true;
}
