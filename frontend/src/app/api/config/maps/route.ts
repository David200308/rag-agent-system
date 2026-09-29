/**
 * GET /api/config/maps
 * Returns the Google Maps browser key at runtime, so it can be set via the
 * container environment instead of being baked into the build.
 *
 * The key is still visible to the browser (the Maps JS API requires it) —
 * protect it in Google Cloud Console with an HTTP-referrer restriction and
 * limit it to the Maps JavaScript API.
 */
export async function GET() {
  return Response.json({
    googleMapsApiKey: process.env.GOOGLE_MAPS_API_KEY || null,
    googleMapsMapId:  process.env.GOOGLE_MAPS_MAP_ID  || null,
  });
}
