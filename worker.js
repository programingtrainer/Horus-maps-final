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
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, If-None-Match",
  "Access-Control-Max-Age": "86400"
};

function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...extraHeaders
    }
  });
}

function normalizeEmailUsername(value) {
  return String(value || "").trim().toLowerCase();
}

function isValidEmailUsername(value) {
  return /^[a-z0-9](?:[a-z0-9._-]{1,22}[a-z0-9])$/.test(value) &&
    !value.includes("..") && !value.includes(".-") &&
    !value.includes("-.");
}

function passwordStrength(password) {
  const groups = [
    /[a-z]/.test(password),
    /[A-Z]/.test(password),
    /[0-9]/.test(password),
    /[^A-Za-z0-9]/.test(password)
  ].filter(Boolean).length;
  if (password.length < 8 || groups < 2) return "weak";
  return password.length >= 12 && groups >= 3 ? "strong" : "medium";
}

function toBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function hashPassword(password, salt) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"]
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: 310000, hash: "SHA-256" },
    key,
    256
  );
  return toBase64Url(new Uint8Array(bits));
}

async function readJson(request) {
  const length = Number(request.headers.get("Content-Length") || 0);
  if (length > 8192) throw new Error("Request too large");
  return await request.json();
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (url.pathname === "/health" && request.method === "GET") {
      return json({ ok: true, service: "horus-maps-final", apiVersion: 1 });
    }

    if (url.pathname === "/v1/config" && request.method === "GET") {
      try {
        const stored = env.HORUS_MAPS_CONFIG
          ? await env.HORUS_MAPS_CONFIG.get("horus:remote-config:v1")
          : null;
        const config = stored ? JSON.parse(stored) : DEFAULT_CONFIG;
        return json({ ok: true, config, source: stored ? "kv" : "default" });
      } catch {
        return json({ ok: false, error: "Configuration unavailable" }, 503);
      }
    }

    if (url.pathname === "/v1/auth/email-availability" && request.method === "POST") {
      if (!env.HORUS_USERS) return json({ ok: false, error: "Database unavailable" }, 503);
      try {
        const body = await readJson(request);
        const username = normalizeEmailUsername(body.username);
        if (!isValidEmailUsername(username)) {
          return json({ ok: false, available: false, error: "اسم البريد غير صالح. استخدم 3 إلى 24 حرفًا إنجليزيًا أو أرقامًا أو نقطة أو شرطة." }, 400);
        }
        const email = username + "@horus.com";
        const existing = await env.HORUS_USERS
          .prepare("SELECT 1 AS found FROM users WHERE email = ? COLLATE NOCASE LIMIT 1")
          .bind(email)
          .first();
        return json({ ok: true, available: !existing, email });
      } catch {
        return json({ ok: false, error: "تعذر التحقق من البريد الآن. حاول مرة أخرى." }, 503);
      }
    }

    if (url.pathname === "/v1/auth/register" && request.method === "POST") {
      if (!env.HORUS_USERS) return json({ ok: false, error: "Database unavailable" }, 503);
      try {
        const body = await readJson(request);
        const firstName = String(body.firstName || "").trim();
        const lastName = String(body.lastName || "").trim();
        const phone = String(body.phone || "").trim();
        const username = normalizeEmailUsername(body.username);
        const password = String(body.password || "");
        const email = username + "@horus.com";

        if (firstName.length < 2 || firstName.length > 50 || lastName.length < 2 || lastName.length > 50) {
          return json({ ok: false, error: "يرجى إدخال الاسم الأول واسم العائلة بشكل صحيح." }, 400);
        }
        if (phone.length > 24 || (phone && !/^\+[1-9][0-9]{6,14}$/.test(phone))) {
          return json({ ok: false, error: "رقم الهاتف الدولي غير صالح." }, 400);
        }
        if (!isValidEmailUsername(username)) {
          return json({ ok: false, error: "اسم بريد Horus غير صالح." }, 400);
        }
        if (password.length > 128) {
          return json({ ok: false, error: "كلمة المرور طويلة جدًا. الحد الأقصى 128 حرفًا." }, 400);
        }
        if (passwordStrength(password) === "weak") {
          return json({ ok: false, error: "كلمة المرور ضعيفة. استخدم 8 أحرف على الأقل ومزيجًا من أنواع مختلفة من الرموز." }, 400);
        }

        const salt = crypto.getRandomValues(new Uint8Array(16));
        const passwordHash = await hashPassword(password, salt);
        const userId = crypto.randomUUID();
        const saltEncoded = toBase64Url(salt);

        const result = await env.HORUS_USERS.prepare(
          "INSERT INTO users (id, first_name, last_name, phone, email, password_hash, password_salt) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).bind(userId, firstName, lastName, phone || null, email, passwordHash, saltEncoded).run();

        if (!result.success || result.meta.changes !== 1) {
          return json({ ok: false, error: "تعذر إنشاء الحساب. حاول مرة أخرى." }, 500);
        }

        return json({
          ok: true,
          message: "تم إنشاء حساب Horus بنجاح.",
          user: { id: userId, firstName, lastName, email }
        }, 201);
      } catch (error) {
        const message = String(error && error.message || "");
        if (message.includes("UNIQUE constraint failed") || message.includes("users.email")) {
          return json({ ok: false, error: "هذا البريد مستخدم بالفعل. اختر اسمًا آخر." }, 409);
        }
        return json({ ok: false, error: "حدث خطأ أثناء إنشاء الحساب. حاول مرة أخرى." }, 503);
      }
    }

    if (request.method !== "GET" && request.method !== "POST") {
      return json({ ok: false, error: "Method not allowed" }, 405, { Allow: "GET, POST, OPTIONS" });
    }

    return json({ ok: false, error: "Not found" }, 404);
  }
};
