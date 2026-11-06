const DEFAULT_CONFIG = {
  schemaVersion: 1,
  app: { name: "HorusMaps", minimumSupportedVersion: 1 },
  map: {
    styleUrl: "https://tiles.openfreemap.org/styles/liberty",
    initialZoom: 2.0,
    showBuildings3D: true,
    language: "device"
  },
  features: {
    currentLocation: true,
    search: false,
    nearbyPlaces: false,
    routing: false,
    categories: false
  },
  ui: { showWelcomePanel: true },
  refresh: { intervalSeconds: 300 }
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, If-None-Match",
  "Access-Control-Max-Age": "86400"
};

function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "public, max-age=60, stale-while-revalidate=300",
      ...extraHeaders
    }
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method !== "GET") {
      return json({ ok: false, error: "Method not allowed" }, 405, {
        Allow: "GET, OPTIONS"
      });
    }

    if (url.pathname === "/health") {
      return json({
        ok: true,
        service: "horus-maps-final",
        apiVersion: 1
      });
    }

    if (url.pathname === "/v1/config") {
      try {
        const stored = env.HORUS_MAPS_CONFIG
          ? await env.HORUS_MAPS_CONFIG.get("horus:remote-config:v1")
          : null;
        const config = stored ? JSON.parse(stored) : DEFAULT_CONFIG;
        return json({
          ok: true,
          config,
          source: stored ? "kv" : "default"
        });
      } catch {
        return json({ ok: false, error: "Configuration unavailable" }, 503);
      }
    }

    return json({ ok: false, error: "Not found" }, 404);
  }
};
