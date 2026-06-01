/**
 * Drop-in wrapper around undici `request` that automatically adds an
 * X-Web-Token header on every server-side request to the Spring Boot backend.
 *
 * The web frontend is server-to-server (Next.js → Spring Boot), so a static
 * pre-shared token is sufficient — no HMAC or timestamp needed.
 * The token is stored in CLIENT_WEB_SECRET and never reaches the browser.
 */

import { request as undiciRequest } from "undici";

type UndiciRequestOptions = NonNullable<Parameters<typeof undiciRequest>[1]>;
type UndiciResponseData = Awaited<ReturnType<typeof undiciRequest>>;

const WEB_TOKEN = process.env.CLIENT_WEB_SECRET ?? "";

function webIdentityHeaders(): Record<string, string> {
  if (!WEB_TOKEN) return {};
  return { "x-web-token": WEB_TOKEN };
}

export async function request(
  url: string | URL,
  options: UndiciRequestOptions,
): Promise<UndiciResponseData> {
  return undiciRequest(url, {
    ...options,
    headers: {
      ...options.headers,
      ...webIdentityHeaders(),
    },
  });
}

// Re-export everything else from undici so routes only need one import
export { fetch as undicicFetch } from "undici";

/**
 * Signed wrapper around the global `fetch` for routes that use the Fetch API
 * directly instead of undici `request`.
 */
export async function backendFetch(
  url: string,
  init: RequestInit = {},
): Promise<Response> {
  return fetch(url, {
    ...init,
    headers: {
      ...(init.headers as Record<string, string> | undefined),
      ...webIdentityHeaders(),
    },
  });
}
