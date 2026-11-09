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
    search: true,
    nearbyPlaces: true,
    routing: false,
    categories: true
  },
  ui: { showWelcomePanel: true },
  refresh: { intervalSeconds: 300 }
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, If-None-Match, Authorization",
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
    { name: "PBKDF2", salt, iterations: 100000, hash: "SHA-256" },
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

function randomSessionToken() {
  return toBase64Url(crypto.getRandomValues(new Uint8Array(32)));
}

async function sha256Base64Url(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return toBase64Url(new Uint8Array(digest));
}

async function createSession(env, userId) {
  const token = randomSessionToken();
  const tokenHash = await sha256Base64Url(token);
  const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
  await env.HORUS_USERS.prepare(
    "INSERT INTO sessions (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)"
  ).bind(crypto.randomUUID(), userId, tokenHash, expiresAt).run();
  return { token, expiresAt };
}

async function getAuthenticatedUser(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer ([A-Za-z0-9_-]{40,100})$/);
  if (!match) return null;
  const tokenHash = await sha256Base64Url(match[1]);
  return await env.HORUS_USERS.prepare(
    "SELECT u.id, u.first_name, u.last_name, u.phone, u.email, u.password_hash, u.password_salt, s.token_hash FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ? LIMIT 1"
  ).bind(tokenHash, new Date().toISOString()).first();
}


const SEARCH_USER_AGENT = "HorusMaps/0.1 (Android map search; https://github.com/programingtrainer/Horus-maps-final)";
const SEARCH_OVERPASS_ENDPOINTS = [
  "https://overpass.private.coffee/api/interpreter",
  "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
  "https://overpass-api.de/api/interpreter"
];

function validCoordinate(value, min, max) {
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max ? number : null;
}

function normalizeSearchText(value) {
  return String(value || "").toLocaleLowerCase().normalize("NFD")
    .replace(/[\u064B-\u065F\u0670]/g, "").replace(/[\u0300-\u036f]/g, "")
    .replace(/[أإآٱ]/g, "ا").replace(/[ىي]/g, "ي").replace(/ة/g, "ه").replace(/ـ/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ").trim().replace(/\s+/g, " ");
}

const GEOGRAPHIC_ALIASES = new Map();
for (const [canonical, aliases] of [
  ["United States", ["امريكا", "الولايات المتحدة", "الولايات المتحده", "الولايات المتحده الامريكيه", "الولايات المتحدة الامريكية", "usa", "u s a", "us", "america", "united states", "united states of america"]],
  ["United Kingdom", ["بريطانيا", "المملكة المتحدة", "المملكه المتحده", "انجلترا", "uk", "u k", "britain", "great britain", "united kingdom"]],
  ["Saudi Arabia", ["السعودية", "السعوديه", "المملكة العربية السعودية", "المملكه العربيه السعوديه", "saudi", "ksa", "saudi arabia"]],
  ["United Arab Emirates", ["الامارات", "الإمارات", "الامارات العربية المتحدة", "الامارات العربيه المتحده", "uae", "united arab emirates"]],
  ["Egypt", ["مصر", "egypt"]], ["Türkiye", ["تركيا", "turkey", "turkiye", "türkiye"]],
  ["Russia", ["روسيا", "russia"]], ["China", ["الصين", "china"]], ["Japan", ["اليابان", "japan"]],
  ["Germany", ["المانيا", "ألمانيا", "germany"]], ["France", ["فرنسا", "france"]],
  ["Canada", ["كندا", "canada"]], ["Australia", ["استراليا", "أستراليا", "australia"]]
]) for (const alias of aliases) GEOGRAPHIC_ALIASES.set(normalizeSearchText(alias), canonical);

function geographicQueryVariants(query) {
  const normalized = normalizeSearchText(query);
  let canonical = GEOGRAPHIC_ALIASES.get(normalized);
  if (!canonical && normalized.length >= 5) {
    for (const [alias, country] of GEOGRAPHIC_ALIASES) {
      if (alias.length >= 5 && levenshteinDistance(normalized, alias) <= 1) { canonical = country; break; }
    }
  }
  return [...new Set((canonical ? [canonical, query] : [query]).map(value => String(value || "").trim()).filter(Boolean))];
}

function levenshteinDistance(left, right) {
  const a = Array.from(left), b = Array.from(right);
  if (Math.abs(a.length - b.length) > 2) return 3;
  let previous = Array.from({ length: b.length + 1 }, (_, index) => index);
  for (let i = 1; i <= a.length; i++) {
    const current = [i];
    for (let j = 1; j <= b.length; j++) current[j] = Math.min(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    previous = current;
  }
  return previous[b.length];
}

const KNOWN_SEARCH_WORDS = ["carrefour", "panda", "danube", "othaim", "nahdi", "lulu"];
function correctKnownSearchTerms(query) {
  return String(query || "").replace(/[\p{L}\p{N}]+/gu, token => {
    const normalized = normalizeSearchText(token);
    for (const canonical of KNOWN_SEARCH_WORDS) {
      const distance = levenshteinDistance(normalized, canonical);
      if (normalized === canonical || (normalized.length >= 4 && distance <= 1) || (canonical.length >= 8 && normalized.length >= canonical.length - 2 && distance <= 2)) return canonical;
    }
    return token;
  });
}
function escapeOverpassRegex(value) {
  return String(value || "")
    .trim()
    .slice(0, 64)
    .replace(/[.*+?^${}()|[\]\\]/g, character => String.fromCharCode(92) + character);
}

function haversineMeters(lat1, lon1, lat2, lon2) {
  const radians = value => value * Math.PI / 180;
  const dLat = radians(lat2 - lat1);
  const dLon = radians(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(radians(lat1)) * Math.cos(radians(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

async function cachedJson(env, cacheKey, ttlSeconds, producer) {
  const key = "horus:search-cache:v1:" + cacheKey;
  if (env.HORUS_MAPS_CONFIG) {
    try {
      const cached = await env.HORUS_MAPS_CONFIG.get(key);
      if (cached) return json(JSON.parse(cached));
    } catch (error) {
      console.warn("[HorusSearch] Cache read failed", String(error?.message || error).slice(0, 120));
    }
  }
  const response = await producer();
  if (response.ok && env.HORUS_MAPS_CONFIG) {
    try {
      const payload = await response.clone().text();
      await env.HORUS_MAPS_CONFIG.put(key, payload, { expirationTtl: ttlSeconds });
    } catch (error) {
      console.warn("[HorusSearch] Cache write failed", String(error?.message || error).slice(0, 120));
    }
  }
  return response;
}

function searchNamePattern(query, category) {
  const value = correctKnownSearchTerms(String(query || "").trim());
  if (!value) return "";
  if (/بنده|panda/i.test(value)) return "بنده|Panda|Panda Hypermarket";
  if (/كارفور|carrefour/i.test(value)) return "كارفور|Carrefour";
  if (/لولو|lulu/i.test(value)) return "لولو|Lulu";
  if (/دانوب|danube/i.test(value)) return "دانوب|Danube";
  if (/العثيم|othaim/i.test(value)) return "العثيم|Othaim";
  if (/النهدي|nahdi/i.test(value)) return "النهدي|Nahdi|Al Nahdi";
  const variants = category === "mosque" ? [value, "مسجد " + value, "جامع " + value] : [value];
  return variants.map(escapeOverpassRegex).filter(Boolean).join("|");
}

function overpassSelectors(category, query) {
  const namePattern = searchNamePattern(query, category);
  const nameFilter = namePattern
    ? '[~"^(name|name:ar|name:en|brand|brand:ar|brand:en)$"~"' + namePattern + '",i]'
    : "";
  if (category === "mosque") {
    return [
      'nwr(around:RADIUS,LAT,LON)["amenity"="place_of_worship"]["religion"~"muslim|islam",i]' + nameFilter + ';',
      'nwr(around:RADIUS,LAT,LON)["building"="mosque"]' + nameFilter + ';'
    ];
  }
  if (category === "supermarket") {
    return [
      'nwr(around:RADIUS,LAT,LON)["shop"~"supermarket|convenience|department_store"]' + nameFilter + ';'
    ];
  }
  if (category === "pharmacy") {
    return [
      'nwr(around:RADIUS,LAT,LON)["amenity"="pharmacy"]' + nameFilter + ';',
      'nwr(around:RADIUS,LAT,LON)["shop"="chemist"]' + nameFilter + ';'
    ];
  }
  if (category === "hospital") {
    return [
      'nwr(around:RADIUS,LAT,LON)["amenity"="hospital"]' + nameFilter + ';',
      'nwr(around:RADIUS,LAT,LON)["healthcare"="hospital"]' + nameFilter + ';'
    ];
  }
  if (category === "restaurant") {
    return [
      'nwr(around:RADIUS,LAT,LON)["amenity"="restaurant"]' + nameFilter + ';',
      'nwr(around:RADIUS,LAT,LON)["amenity"="fast_food"]' + nameFilter + ';'
    ];
  }
  if (category === "fuel") {
    return ['nwr(around:RADIUS,LAT,LON)["amenity"="fuel"]' + nameFilter + ';'];
  }
  return [];
}

const COUNTRY_ISO_CODES = {
  "United States": "US",
  "United Kingdom": "GB",
  "Saudi Arabia": "SA",
  "United Arab Emirates": "AE",
  "Egypt": "EG",
  "Türkiye": "TR",
  "Russia": "RU",
  "China": "CN",
  "Japan": "JP",
  "Germany": "DE",
  "France": "FR",
  "Canada": "CA",
  "Australia": "AU"
};
const COUNTRY_CENTERS = {
  US: [39.8, -98.5], GB: [54.0, -2.0], SA: [24.0, 45.0], AE: [24.0, 54.0],
  EG: [26.8, 30.8], TR: [39.0, 35.0], RU: [60.0, 100.0], CN: [35.0, 103.0],
  JP: [36.0, 138.0], DE: [51.0, 10.0], FR: [46.0, 2.0], CA: [56.0, -106.0],
  AU: [-25.0, 133.0]
};

const COUNTRY_NAMES = {
  SA: "السعودية", EG: "مصر", QA: "قطر", AE: "الإمارات", KW: "الكويت",
  BH: "البحرين", OM: "عُمان", JO: "الأردن", LB: "لبنان", IQ: "العراق",
  US: "الولايات المتحدة", GB: "المملكة المتحدة", CA: "كندا", AU: "أستراليا",
  DE: "ألمانيا", FR: "فرنسا", TR: "تركيا", CN: "الصين", JP: "اليابان",
  RU: "روسيا", IN: "الهند", PK: "باكستان", BD: "بنغلاديش", IT: "إيطاليا",
  ES: "إسبانيا", NL: "هولندا", BR: "البرازيل", MX: "المكسيك"
};

function countryNameFromCode(code) {
  const normalized = String(code || "").toUpperCase();
  return COUNTRY_NAMES[normalized] || normalized;
}

async function resolveCountryScope(query) {
  const words = String(query || "").trim().split(/\s+/).filter(Boolean);
  if (words.length < 2) return null;
  const connectors = new Set(["in", "داخل", "في", "ب", "بدولة", "دوله"]);
  for (let count = Math.min(4, words.length - 1); count >= 1; count--) {
    let suffixWords = words.slice(-count);
    while (suffixWords.length && connectors.has(normalizeSearchText(suffixWords[0]))) suffixWords = suffixWords.slice(1);
    if (!suffixWords.length) continue;
    const suffix = suffixWords.join(" ");
    const normalizedSuffix = normalizeSearchText(suffix);
    let canonicalCountry = GEOGRAPHIC_ALIASES.get(normalizedSuffix);
    if (!canonicalCountry && normalizedSuffix.length >= 5) {
      for (const [alias, canonical] of GEOGRAPHIC_ALIASES) {
        if (alias.length >= 5 && levenshteinDistance(normalizedSuffix, alias) <= 1) {
          canonicalCountry = canonical;
          break;
        }
      }
    }
    const knownCode = canonicalCountry ? COUNTRY_ISO_CODES[canonicalCountry] : null;
    if (knownCode) {
      const center = COUNTRY_CENTERS[knownCode] || [NaN, NaN];
      return {
        countryCode: knownCode,
        query: words.slice(0, words.length - count).join(" ").replace(/\\b(in)\\s*$/i, "").trim(),
        matchedWords: suffixWords.length,
        latitude: center[0],
        longitude: center[1]
      };
    }
    const target = new URL("https://photon.komoot.io/api/");
    target.searchParams.set("q", suffix);
    target.searchParams.set("limit", "8");
    target.searchParams.set("lang", /[\u0600-\u06FF]/.test(suffix) ? "ar" : "en");
    target.searchParams.set("layer", "country");
    try {
      const response = await fetch(target.toString(), {
        headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/geo+json, application/json" },
        signal: AbortSignal.timeout(6500)
      });
      if (!response.ok) continue;
      const payload = await response.json();
      const feature = (payload.features || []).find(item => {
        const p = item.properties || {};
        const type = String(p.type || p.layer || "").toLowerCase();
        const osmCountry = String(p.osm_key || "").toLowerCase() === "place" && String(p.osm_value || "").toLowerCase() === "country";
        const name = normalizeSearchText(p.name || "");
        return (type === "country" || osmCountry) &&
          (name === normalizeSearchText(suffix) || normalizeSearchText(p.country || "") === normalizeSearchText(suffix));
      });
      if (!feature) continue;
      const p = feature.properties || {};
      const code = String(p.countrycode || "").toUpperCase();
      const coordinates = feature.geometry?.coordinates || [];
      if (!/^[A-Z]{2}$/.test(code)) continue;
      return {
        countryCode: code,
        query: words.slice(0, words.length - count).join(" ").replace(/\b(in)\s*$/i, "").trim(),
        matchedWords: suffixWords.length,
        latitude: Number(coordinates[1]),
        longitude: Number(coordinates[0])
      };
    } catch (error) {
      console.warn("[HorusSearch] Country scope resolution failed", String(error?.message || error).slice(0, 100));
    }
  }
  return null;
}

async function resolvePlaceScope(query, nearLat, nearLon) {
  const value = String(query || "").trim().slice(0, 100);
  if (value.length < 2) return null;

  const target = new URL("https://nominatim.openstreetmap.org/search");
  target.searchParams.set("q", value);
  target.searchParams.set("format", "jsonv2");
  target.searchParams.set("addressdetails", "1");
  target.searchParams.set("limit", "8");
  target.searchParams.set("accept-language", "ar,en");
  if (Number.isFinite(nearLat) && Number.isFinite(nearLon)) {
    const delta = 4;
    target.searchParams.set("viewbox", [
      nearLon - delta, nearLat + delta, nearLon + delta, nearLat - delta
    ].join(","));
  }

  try {
    const response = await fetch(target.toString(), {
      headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/json" },
      signal: AbortSignal.timeout(10000)
    });
    if (!response.ok) return null;
    const payload = await response.json();
    const allowedTypes = new Set([
      "city", "town", "village", "hamlet", "suburb", "neighbourhood", "quarter",
      "district", "county", "state", "province", "municipality", "administrative",
      "locality", "isolated_dwelling", "borough", "residential", "primary",
      "secondary", "tertiary", "unclassified", "service", "pedestrian",
      "living_street", "road"
    ]);
    const candidate = (Array.isArray(payload) ? payload : []).find(item => {
      const category = String(item.class || item.category || "").toLowerCase();
      const type = String(item.type || "").toLowerCase();
      return ["place", "boundary", "highway"].includes(category) && allowedTypes.has(type) &&
        /^[a-z]{2}$/i.test(String(item.address?.country_code || ""));
    });
    if (!candidate) return null;

    const bbox = Array.isArray(candidate.boundingbox) ? candidate.boundingbox.map(Number) : [];
    const bounds = bbox.length === 4 && bbox.every(Number.isFinite)
      ? { south: bbox[0], north: bbox[1], west: bbox[2], east: bbox[3] }
      : null;
    const latitude = Number(candidate.lat);
    const longitude = Number(candidate.lon);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;

    return {
      countryCode: String(candidate.address.country_code).toUpperCase(),
      latitude,
      longitude,
      bounds,
      osmType: String(candidate.osm_type || "").toLowerCase(),
      osmId: String(candidate.osm_id || ""),
      category: String(candidate.class || "").toLowerCase(),
      type: String(candidate.type || "").toLowerCase(),
      name: String(candidate.name || candidate.display_name || value)
    };
  } catch (error) {
    console.warn("[HorusSearch] Place scope resolution failed", String(error?.message || error).slice(0, 140));
    return null;
  }
}

async function queryOverpassPlaceScope(scope, category) {
  const isRelationArea = scope.osmType === "relation" && /^\\d+$/.test(scope.osmId) && scope.category === "boundary";
  let selectors;
  let overpassQuery;

  if (isRelationArea) {
    const areaId = Number(scope.osmId) + 3600000000;
    selectors = overpassSelectors(category, "")
      .map(item => item.replaceAll("nwr(around:RADIUS,LAT,LON)", "nwr(area.searchArea)"))
      .join("\\n");
    overpassQuery = '[out:json][timeout:18];area(' + areaId + ')->.searchArea;(' + selectors + ');out center tags 100;';
  } else if (scope.bounds) {
    const { south, north, west, east } = scope.bounds;
    selectors = overpassSelectors(category, "")
      .map(item => item.replaceAll("nwr(around:RADIUS,LAT,LON)", "nwr(" + south + "," + west + "," + north + "," + east + ")"))
      .join("\\n");
    overpassQuery = '[out:json][timeout:18];(' + selectors + ');out center tags 100;';
  } else {
    const radius = scope.type === "city" || scope.type === "town" ? 12000 : 4500;
    return await queryOverpass(scope.latitude, scope.longitude, scope.countryCode, category, "", radius);
  }

  const attempts = SEARCH_OVERPASS_ENDPOINTS.map(async endpoint => {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "User-Agent": SEARCH_USER_AGENT
      },
      body: "data=" + encodeURIComponent(overpassQuery),
      signal: AbortSignal.timeout(20000)
    });
    if (!response.ok) throw new Error(new URL(endpoint).hostname + " HTTP " + response.status);
    const data = await response.json();
    if (!Array.isArray(data.elements)) throw new Error(new URL(endpoint).hostname + " returned invalid data");
    return data.elements;
  });
  try {
    return await Promise.any(attempts);
  } catch (error) {
    const failures = Array.isArray(error?.errors) ? error.errors : [];
    const details = failures.map(item => String(item?.message || item).slice(0, 100)).join("; ");
    throw new Error(details || "Place-scope Overpass search unavailable");
  }
}

async function queryPhotonGlobal(category, query, centerLat, centerLon) {
  const tags = PHOTON_TAGS[category] || [];
  const searchText = photonSearchText(correctKnownSearchTerms(query));
  if (!tags.length) return [];
  const attempts = tags.map(async tag => {
    const target = new URL("https://photon.komoot.io/api/");
    target.searchParams.set("lat", String(centerLat));
    target.searchParams.set("lon", String(centerLon));
    target.searchParams.set("limit", "50");
    target.searchParams.set("lang", "en");
    target.searchParams.set("distance_sort", "true");
    if (searchText) {
      target.searchParams.set("q", searchText);
      target.searchParams.set("osm_tag", tag);
    } else {
      const [key, value] = tag.split(":");
      target.searchParams.set("include", "osm." + key + "." + value);
    }
    const response = await fetch(target.toString(), {
      headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/geo+json, application/json" },
      signal: AbortSignal.timeout(10000)
    });
    if (!response.ok) throw new Error("Photon global search HTTP " + response.status);
    const payload = await response.json();
    if (!Array.isArray(payload.features)) throw new Error("Photon global search returned invalid data");
    return payload.features;
  });

  const settled = await Promise.allSettled(attempts);
  const seen = new Set();
  const elements = [];
  for (const result of settled) {
    if (result.status !== "fulfilled") continue;
    for (const feature of result.value) {
      const properties = feature.properties || {};
      const coordinates = feature.geometry?.coordinates || [];
      const lat = Number(coordinates[1]), lon = Number(coordinates[0]);
      const osmType = String(properties.osm_type || "N");
      const osmId = String(properties.osm_id || "");
      const id = osmType + "/" + osmId;
      if (!Number.isFinite(lat) || !Number.isFinite(lon) || !osmId || seen.has(id)) continue;
      seen.add(id);
      const tagsForElement = { ...(properties.extra || {}) };
      if (properties.name) tagsForElement.name = properties.name;
      if (properties.brand) tagsForElement.brand = properties.brand;
      if (properties.city || properties.town || properties.village) tagsForElement["addr:city"] = properties.city || properties.town || properties.village;
      if (properties.state) tagsForElement["addr:state"] = properties.state;
      if (properties.country) tagsForElement["addr:country"] = properties.country;
      if (properties.suburb || properties.district) tagsForElement["addr:suburb"] = properties.suburb || properties.district;
      if (properties.countrycode) tagsForElement["addr:country_code"] = properties.countrycode;
      if (properties.osm_key && properties.osm_value) tagsForElement[properties.osm_key] = properties.osm_value;
      elements.push({
        type: osmType === "W" ? "way" : osmType === "R" ? "relation" : "node",
        id: osmId,
        lat,
        lon,
        tags: tagsForElement
      });
    }
  }
  console.info("[HorusSearch] Global Photon fallback completed", category, elements.length);
  return elements;
}

async function queryPhotonCountry(countryCode, category, query, centerLat, centerLon) {
  const tags = PHOTON_TAGS[category] || [];
  const searchText = photonSearchText(correctKnownSearchTerms(query));
  if (!tags.length) return [];
  const attempts = tags.map(async tag => {
    const target = new URL("https://photon.komoot.io/api/");
    target.searchParams.set("countrycode", countryCode.toUpperCase());
    target.searchParams.set("limit", "50");
    target.searchParams.set("lang", "en");
    if (searchText) {
      target.searchParams.set("q", searchText);
      target.searchParams.set("osm_tag", tag);
    } else {
      const [key, value] = tag.split(":");
      target.searchParams.set("include", "osm." + key + "." + value);
    }
    const response = await fetch(target.toString(), {
      headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/geo+json, application/json" },
      signal: AbortSignal.timeout(10000)
    });
    if (!response.ok) throw new Error("Photon country search HTTP " + response.status);
    const payload = await response.json();
    if (!Array.isArray(payload.features)) throw new Error("Photon country search returned invalid data");
    return payload.features;
  });
  const settled = await Promise.allSettled(attempts);
  const seen = new Set();
  const elements = [];
  for (const result of settled) {
    if (result.status !== "fulfilled") continue;
    for (const feature of result.value) {
      const properties = feature.properties || {};
      if (String(properties.countrycode || "").toUpperCase() !== countryCode) continue;
      const coordinates = feature.geometry?.coordinates || [];
      const lat = Number(coordinates[1]), lon = Number(coordinates[0]);
      const osmType = String(properties.osm_type || "N");
      const osmId = String(properties.osm_id || "");
      const id = osmType + "/" + osmId;
      if (!Number.isFinite(lat) || !Number.isFinite(lon) || !osmId || seen.has(id)) continue;
      seen.add(id);
      const tagsForElement = { ...(properties.extra || {}) };
      if (properties.name) tagsForElement.name = properties.name;
      if (properties.brand) tagsForElement.brand = properties.brand;
      if (properties.city || properties.town || properties.village) tagsForElement["addr:city"] = properties.city || properties.town || properties.village;
      if (properties.state || properties.county) tagsForElement["addr:state"] = properties.state || properties.county;
      if (properties.country) tagsForElement["addr:country"] = properties.country;
      if (properties.suburb || properties.district) tagsForElement["addr:suburb"] = properties.suburb || properties.district;
      if (properties.osm_key && properties.osm_value) tagsForElement[properties.osm_key] = properties.osm_value;
      elements.push({
        type: osmType === "W" ? "way" : osmType === "R" ? "relation" : "node",
        id: osmId,
        lat,
        lon,
        tags: tagsForElement
      });
    }
  }
  console.info("[HorusSearch] Photon country fallback completed", countryCode, category, elements.length);
  return elements;
}

async function queryOverpassCountry(countryCode, category, query) {
  const selectors = overpassSelectors(category, query)
    .map(item => item.replaceAll("nwr(around:RADIUS,LAT,LON)", "nwr(area.searchArea)"))
    .join("\n");
  const overpassQuery = '[out:json][timeout:20];area["ISO3166-1"="' + countryCode + '"]["admin_level"="2"]->.searchArea;(' + selectors + ');out center tags 80;';
  const attempts = SEARCH_OVERPASS_ENDPOINTS.map(async endpoint => {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "User-Agent": SEARCH_USER_AGENT
      },
      body: "data=" + encodeURIComponent(overpassQuery),
      signal: AbortSignal.timeout(22000)
    });
    if (!response.ok) throw new Error(new URL(endpoint).hostname + " HTTP " + response.status);
    const data = await response.json();
    if (!Array.isArray(data.elements)) throw new Error(new URL(endpoint).hostname + " returned invalid data");
    return data.elements;
  });
  try {
    return await Promise.any(attempts);
  } catch (error) {
    const failures = Array.isArray(error?.errors) ? error.errors : [];
    const details = failures.map(item => String(item?.message || item).slice(0, 100)).join("; ");
    throw new Error(details || "Country-wide Overpass search unavailable");
  }
}

async function queryOverpass(lat, lon, countryCode, category, query, radius) {
  // The radius already constrains the search around the device position. Avoid
  // a country-area lookup here: it adds an expensive global area dependency
  // and can make otherwise simple nearby-POI queries time out.
  const selectors = overpassSelectors(category, query)
    .map(item => item.replaceAll("RADIUS", String(radius)).replaceAll("LAT", String(lat)).replaceAll("LON", String(lon)))
    .join("\n");
  const overpassQuery = '[out:json][timeout:8];(' + selectors + ');out center tags 80;';

  // Try public Overpass instances concurrently. Sequential retries could use
  // most of the Android request timeout even when one mirror is healthy.
  const attempts = SEARCH_OVERPASS_ENDPOINTS.map(async endpoint => {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "User-Agent": SEARCH_USER_AGENT
      },
      body: "data=" + encodeURIComponent(overpassQuery),
      signal: AbortSignal.timeout(9000)
    });
    if (!response.ok) throw new Error(new URL(endpoint).hostname + " HTTP " + response.status);
    const data = await response.json();
    if (!Array.isArray(data.elements)) throw new Error(new URL(endpoint).hostname + " returned invalid data");
    return data.elements;
  });

  try {
    return await Promise.any(attempts);
  } catch (error) {
    const failures = Array.isArray(error?.errors) ? error.errors : [];
    const details = failures.map(item => String(item?.message || item).slice(0, 100)).join("; ");
    console.error("[HorusSearch] All Overpass mirrors failed", details);
    throw new Error(details || "Overpass unavailable");
  }
}

const PHOTON_TAGS = {
  mosque: ["amenity:place_of_worship"],
  supermarket: ["shop:supermarket", "shop:convenience", "shop:department_store"],
  pharmacy: ["amenity:pharmacy", "shop:chemist"],
  hospital: ["amenity:hospital", "healthcare:hospital"],
  restaurant: ["amenity:restaurant", "amenity:fast_food"],
  fuel: ["amenity:fuel"]
};

function photonSearchText(query) {
  const value = String(query || "").trim();
  if (/بنده|panda/i.test(value)) return "Panda";
  if (/كارفور|carrefour/i.test(value)) return "Carrefour";
  if (/لولو|lulu/i.test(value)) return "Lulu";
  if (/دانوب|danube/i.test(value)) return "Danube";
  if (/العثيم|othaim/i.test(value)) return "Othaim";
  if (/النهدي|nahdi/i.test(value)) return "Nahdi";
  return value;
}

async function queryPhoton(lat, lon, category, query, radius) {
  const tags = PHOTON_TAGS[category] || [];
  if (!tags.length) throw new Error("Photon does not support this place category");

  const searchText = photonSearchText(correctKnownSearchTerms(query));
  const attempts = tags.map(async tag => {
    const target = new URL(searchText ? "https://photon.komoot.io/api/" : "https://photon.komoot.io/reverse");
    target.searchParams.set("lat", String(lat));
    target.searchParams.set("lon", String(lon));
    target.searchParams.set("limit", "50");
    target.searchParams.set("lang", "en");
    target.searchParams.set("osm_tag", tag);
    if (searchText) {
      target.searchParams.set("q", searchText);
      target.searchParams.set("zoom", "10");
      target.searchParams.set("location_bias_scale", "0.1");
    } else {
      target.searchParams.set("radius", String(radius / 1000));
      target.searchParams.set("distance_sort", "true");
    }

    const response = await fetch(target.toString(), {
      headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/geo+json, application/json" },
      signal: AbortSignal.timeout(10000)
    });
    if (!response.ok) throw new Error("Photon " + tag + " HTTP " + response.status);
    const payload = await response.json();
    if (!Array.isArray(payload.features)) throw new Error("Photon " + tag + " returned invalid data");
    return payload.features;
  });

  const settled = await Promise.allSettled(attempts);
  const successful = settled.filter(item => item.status === "fulfilled");
  if (!successful.length) {
    const details = settled.map(item => item.status === "rejected" ? String(item.reason?.message || item.reason) : "")
      .filter(Boolean).join("; ");
    throw new Error(details || "Photon unavailable");
  }

  const seen = new Set();
  const elements = [];
  for (const attempt of successful) {
    for (const feature of attempt.value) {
      const properties = feature.properties || {};
      const coordinates = feature.geometry?.coordinates || [];
      const elementLat = Number(coordinates[1]);
      const elementLon = Number(coordinates[0]);
      if (!Number.isFinite(elementLat) || !Number.isFinite(elementLon)) continue;
      if (haversineMeters(lat, lon, elementLat, elementLon) > radius) continue;
      const osmType = String(properties.osm_type || "N");
      const osmId = String(properties.osm_id || "");
      const id = osmType + "/" + osmId;
      if (!osmId || seen.has(id)) continue;
      seen.add(id);

      const tagsForElement = { ...(properties.extra || {}) };
      if (properties.name) tagsForElement.name = properties.name;
      if (properties.brand) tagsForElement.brand = properties.brand;
      if (properties.street) tagsForElement["addr:street"] = properties.street;
      if (properties.city || properties.town || properties.village) {
        tagsForElement["addr:city"] = properties.city || properties.town || properties.village;
      }
      if (properties.osm_key && properties.osm_value) {
        tagsForElement[properties.osm_key] = properties.osm_value;
      }
      if (category === "mosque") {
        const religion = String(tagsForElement.religion || "").toLowerCase();
        if (religion && religion !== "muslim" && religion !== "islam") continue;
      }
      elements.push({
        type: osmType === "W" ? "way" : osmType === "R" ? "relation" : "node",
        id: osmId,
        lat: elementLat,
        lon: elementLon,
        tags: tagsForElement
      });
    }
  }
  console.info("[HorusSearch] Photon fallback completed", category, elements.length);
  return elements;
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


    if (url.pathname === "/v1/search/geocode" && request.method === "GET") {
      const rawQuery = String(url.searchParams.get("q") || "").trim().slice(0, 120);
      if (rawQuery.length < 2) return json({ ok: false, error: "اكتب حرفين على الأقل للبحث." }, 400);
      const query = correctKnownSearchTerms(rawQuery);
      const queryVariants = geographicQueryVariants(query);
      const latitude = validCoordinate(url.searchParams.get("lat"), -90, 90);
      const longitude = validCoordinate(url.searchParams.get("lon"), -180, 180);
      const language = String(url.searchParams.get("lang") || "ar,en").slice(0, 32);
      const cacheKey = "geocode:v2:" + queryVariants.join("|").toLocaleLowerCase() + ":" + language + ":" +
        (latitude === null ? "" : latitude.toFixed(2)) + ":" + (longitude === null ? "" : longitude.toFixed(2));
      return cachedJson(env, cacheKey, 86400, async () => {
        const target = new URL("https://nominatim.openstreetmap.org/search");
        target.searchParams.set("q", queryVariants[0]);
        target.searchParams.set("format", "geojson");
        target.searchParams.set("polygon_geojson", "1");
        target.searchParams.set("polygon_threshold", "0.003");
        target.searchParams.set("addressdetails", "1");
        target.searchParams.set("limit", "8");
        target.searchParams.set("accept-language", language);
        if (latitude !== null && longitude !== null) {
          const delta = 3;
          target.searchParams.set("viewbox", [
            longitude - delta, latitude + delta, longitude + delta, latitude - delta
          ].join(","));
        }
        let payload = null;
        try {
          const response = await fetch(target.toString(), {
            headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/geo+json, application/json" },
            signal: AbortSignal.timeout(12000)
          });
          if (response.ok) payload = await response.json();
          else console.warn("[HorusSearch] Nominatim returned HTTP", response.status);
        } catch (error) {
          console.warn("[HorusSearch] Nominatim unavailable; trying fallback", String(error?.message || error).slice(0, 120));
        }

        // Nominatim may throttle or return no matches for some names. Photon is
        // a second independent OSM geocoder so ordinary places remain searchable.
        if (!payload || !Array.isArray(payload.features) || payload.features.length === 0) {
          for (const variant of queryVariants) {
            const fallback = new URL("https://photon.komoot.io/api/");
            fallback.searchParams.set("q", variant);
            fallback.searchParams.set("limit", "8");
            fallback.searchParams.set("lang", language.split(",")[0] || "en");
            if (latitude !== null && longitude !== null) {
              fallback.searchParams.set("lat", String(latitude));
              fallback.searchParams.set("lon", String(longitude));
            }
            const fallbackResponse = await fetch(fallback.toString(), {
              headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/geo+json, application/json" },
              signal: AbortSignal.timeout(12000)
            });
            if (fallbackResponse.ok) {
              payload = await fallbackResponse.json();
              if (Array.isArray(payload.features) && payload.features.length > 0) break;
            }
          }
          if (payload && Array.isArray(payload.features)) {
              payload.features = payload.features.map(feature => {
                const properties = feature.properties || {};
                const displayName = [
                  properties.name,
                  properties.street,
                  properties.housenumber,
                  properties.city || properties.town || properties.village,
                  properties.state,
                  properties.country
                ].filter(Boolean).join(", ");
                return {
                  ...feature,
                  properties: {
                    ...properties,
                    name: properties.name || properties.street || properties.city || properties.country || query,
                    display_name: properties.display_name || displayName || query,
                    type: properties.type || properties.osm_value || "place"
                  }
                };
            });
          }
        }

        if (!payload || !Array.isArray(payload.features)) {
          return json({ ok: false, error: "تعذر الاتصال بخدمات البحث الجغرافي الآن." }, 503);
        }
        return json({ ok: true, ...payload });
      });
    }

    if (url.pathname === "/v1/search/reverse" && request.method === "GET") {
      const latitude = validCoordinate(url.searchParams.get("lat"), -90, 90);
      const longitude = validCoordinate(url.searchParams.get("lon"), -180, 180);
      if (latitude === null || longitude === null) return json({ ok: false, error: "إحداثيات الموقع غير صالحة." }, 400);
      const cacheKey = "reverse:v1:" + latitude.toFixed(3) + ":" + longitude.toFixed(3);
      return cachedJson(env, cacheKey, 86400, async () => {
        const target = new URL("https://nominatim.openstreetmap.org/reverse");
        target.searchParams.set("lat", String(latitude));
        target.searchParams.set("lon", String(longitude));
        target.searchParams.set("format", "jsonv2");
        target.searchParams.set("zoom", "3");
        target.searchParams.set("addressdetails", "1");
        target.searchParams.set("accept-language", "ar,en");
        const response = await fetch(target.toString(), {
          headers: { "User-Agent": SEARCH_USER_AGENT, "Accept": "application/json" },
          signal: AbortSignal.timeout(15000)
        });
        if (!response.ok) return json({ ok: false, error: "تعذر تحديد الدولة من موقعك." }, response.status);
        const payload = await response.json();
        return json({
          ok: true,
          countryCode: String(payload.address?.country_code || "").toLowerCase(),
          country: String(payload.address?.country || ""),
          address: payload.address || {}
        });
      });
    }

    if (url.pathname === "/v1/search/places" && request.method === "POST") {
      try {
        const body = await readJson(request);
        const latitude = validCoordinate(body.latitude, -90, 90);
        const longitude = validCoordinate(body.longitude, -180, 180);
        let countryCode = String(body.countryCode || "").trim().toUpperCase();
        const category = String(body.category || "").trim().toLowerCase();
        let query = String(body.query || "").trim().slice(0, 64);
        const rawQuery = String(body.rawQuery || query).trim().slice(0, 120);
        const countryScope = await resolveCountryScope(rawQuery);
        const globalCountrySearch = Boolean(countryScope);
        if (countryScope) {
          countryCode = countryScope.countryCode;
          const queryWords = query.split(/\\s+/).filter(Boolean);
          query = queryWords.slice(0, Math.max(0, queryWords.length - countryScope.matchedWords)).join(" ").trim();
        }
        const placeScope = !countryScope && query
          ? await resolvePlaceScope(query, latitude, longitude)
          : null;
        if (placeScope) countryCode = placeScope.countryCode;
        const allowed = new Set(["mosque", "supermarket", "hospital", "pharmacy", "restaurant", "fuel"]);
        if (latitude === null || longitude === null || !/^[A-Z]{2}$/.test(countryCode) || !allowed.has(category)) {
          return json({ ok: false, error: "بيانات البحث عن الأماكن غير مكتملة أو غير صالحة." }, 400);
        }
        const sortLatitude = globalCountrySearch && Number.isFinite(countryScope.latitude)
          ? countryScope.latitude
          : placeScope && Number.isFinite(placeScope.latitude) ? placeScope.latitude : latitude;
        const sortLongitude = globalCountrySearch && Number.isFinite(countryScope.longitude)
          ? countryScope.longitude
          : placeScope && Number.isFinite(placeScope.longitude) ? placeScope.longitude : longitude;
        const scopeKey = globalCountrySearch ? "country" : placeScope
          ? "place:" + placeScope.osmType + ":" + placeScope.osmId
          : latitude.toFixed(3) + ":" + longitude.toFixed(3);
        const cacheKey = "places:v4:" + countryCode + ":" + category + ":" + query.toLocaleLowerCase() + ":" + scopeKey;
        // Await the cached operation so upstream failures are caught by this
        // route's handler and returned as a controlled API error, not a Worker 500.
        return await cachedJson(env, cacheKey, 900, async () => {
          let elements = [];
          let usedRadius = globalCountrySearch || placeScope ? 0 : 25000;
          if (placeScope) {
            try {
              elements = await queryOverpassPlaceScope(placeScope, category);
            } catch (scopeError) {
              console.warn("[HorusSearch] Administrative-area search unavailable", String(scopeError?.message || scopeError).slice(0, 160));
            }
            if (elements.length === 0) {
              const countryFallback = await queryPhotonCountry(
                countryCode, category, "", sortLatitude, sortLongitude
              );
              if (placeScope.bounds) {
                const { south, north, west, east } = placeScope.bounds;
                elements = countryFallback.filter(item => {
                  const lat = Number(item.lat ?? item.center?.lat);
                  const lon = Number(item.lon ?? item.center?.lon);
                  return lat >= south && lat <= north && lon >= west && lon <= east;
                });
              } else {
                elements = countryFallback.filter(item =>
                  haversineMeters(sortLatitude, sortLongitude, Number(item.lat), Number(item.lon)) <= 15000
                );
              }
            }
            elements = elements.map(item => ({ ...item, __horusScopeRank: 0 }));
          } else if (globalCountrySearch) {
            if (!query) {
              elements = await queryPhotonCountry(countryCode, category, query, sortLatitude, sortLongitude);
            } else {
              try {
                elements = await queryOverpassCountry(countryCode, category, query);
              } catch (overpassError) {
                console.warn("[HorusSearch] Country Overpass unavailable; trying Photon", String(overpassError?.message || overpassError).slice(0, 160));
              }
              if (elements.length === 0) {
                elements = await queryPhotonCountry(countryCode, category, query, sortLatitude, sortLongitude);
              }
            }
            elements = elements.map(item => ({ ...item, __horusScopeRank: 1 }));
          } else {
            let nearbyElements = [];
            try {
              for (const radius of [10000, 25000]) {
                usedRadius = radius;
                nearbyElements = await queryOverpass(latitude, longitude, countryCode, category, query, radius);
                if (nearbyElements.length >= 5 || radius === 25000) break;
              }
            } catch (overpassError) {
              console.warn("[HorusSearch] Nearby Overpass unavailable", String(overpassError?.message || overpassError).slice(0, 160));
            }
            if (nearbyElements.length === 0) {
              nearbyElements = await queryPhoton(latitude, longitude, category, query, 25000);
            }
            elements = nearbyElements.map(item => ({ ...item, __horusScopeRank: 0 }));

            // Expand from nearby matches to the rest of the user's country,
            // then to other countries only when the local/country result set is sparse.
            if (elements.length < 5) {
              let countryElements = [];
              try {
                countryElements = !query
                  ? await queryPhotonCountry(countryCode, category, "", latitude, longitude)
                  : await queryOverpassCountry(countryCode, category, query);
              } catch (countryError) {
                console.warn("[HorusSearch] Country expansion unavailable", String(countryError?.message || countryError).slice(0, 150));
              }
              if (countryElements.length === 0) {
                try {
                  countryElements = await queryPhotonCountry(countryCode, category, query, latitude, longitude);
                } catch (photonError) {
                  console.warn("[HorusSearch] Country Photon fallback unavailable", String(photonError?.message || photonError).slice(0, 120));
                }
              }
              elements.push(...countryElements.map(item => ({ ...item, __horusScopeRank: 1 })));
            }

            if (elements.length < 5) {
              try {
                const globalElements = await queryPhotonGlobal(category, query, latitude, longitude);
                elements.push(...globalElements.map(item => ({ ...item, __horusScopeRank: 2 })));
              } catch (globalError) {
                console.warn("[HorusSearch] Global fallback unavailable", String(globalError?.message || globalError).slice(0, 140));
              }
            }
          }
          const results = [];
          const seen = new Set();
          for (const element of elements) {
            const lat = Number(element.lat ?? element.center?.lat);
            const lon = Number(element.lon ?? element.center?.lon);
            if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
            const id = String(element.type || "osm") + "/" + String(element.id || "");
            if (seen.has(id)) continue;
            seen.add(id);
            const tags = element.tags || {};
            const name = String(tags.name || tags["name:ar"] || tags.brand || tags["brand:ar"] || tags["name:en"] || "مكان بدون اسم");
            results.push({
              id,
              name,
              latitude: lat,
              longitude: lon,
              type: category,
              distanceMeters: Math.round(haversineMeters(sortLatitude, sortLongitude, lat, lon)),
              scopeRank: Number(element.__horusScopeRank || 0),
              address: [tags["addr:street"], tags["addr:suburb"], tags["addr:city"] || tags["addr:town"]].filter(Boolean).join("، "),
              brand: String(tags.brand || ""),
              openingHours: String(tags.opening_hours || ""),
              region: String(tags["addr:state"] || tags["addr:province"] || tags["addr:region"] || tags["is_in:state"] || ""),
              country: String(tags["addr:country"] || tags["is_in:country"] || countryNameFromCode(countryCode))
            });
          }
          results.sort((a, b) =>
            (a.scopeRank - b.scopeRank) || (a.distanceMeters - b.distanceMeters)
          );
          return json({
            ok: true,
            countryCode: countryCode.toLowerCase(),
            searchScope: placeScope ? "administrative-area" : globalCountrySearch ? "country" : "nearby+country+global",
            resolvedArea: placeScope?.name || "",
            radiusMeters: usedRadius,
            results: results.slice(0, 40)
          });
        });
      } catch (error) {
        console.error("[HorusSearch] Nearby search failed", String(error?.message || error).slice(0, 220));
        return json({ ok: false, error: "تعذر تحميل الأماكن القريبة الآن. حاول مرة أخرى." }, 503);
      }
    }
    if (url.pathname === "/v1/auth/email-availability" && request.method === "POST") {
      if (!env.HORUS_USERS) return json({ ok: false, error: "Database unavailable" }, 503);
      let registerStage = "request_parse";
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
      let registerStage = "request_parse";
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

        registerStage = "password_hash";
        registerStage = "password_hash";
        const salt = crypto.getRandomValues(new Uint8Array(16));
        const passwordHash = await hashPassword(password, salt);
        const userId = crypto.randomUUID();
        const saltEncoded = toBase64Url(salt);

        registerStage = "database_insert";
        registerStage = "database_insert";
        const result = await env.HORUS_USERS.prepare(
          "INSERT INTO users (id, first_name, last_name, phone, email, password_hash, password_salt) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).bind(userId, firstName, lastName, phone || null, email, passwordHash, saltEncoded).run();

        if (!result.success || result.meta?.changes !== 1) {
          const errorCode = "AUTH_REGISTER_DATABASE_INSERT_NO_CHANGE";
          console.error("[HorusAuth] Registration insert returned no change", {
            stage: registerStage,
            errorCode,
            changes: result.meta?.changes ?? null
          });
          return json({
            ok: false,
            error: "تعذر حفظ الحساب في قاعدة البيانات. (رمز: " + errorCode + ")",
            errorCode
          }, 500);
        }

        const session = await createSession(env, userId);
        return json({
          ok: true,
          message: "تم إنشاء حساب Horus بنجاح.",
          sessionToken: session.token,
          expiresAt: session.expiresAt,
          user: { id: userId, firstName, lastName, email }
        }, 201);
      } catch (error) {
        const message = String(error && error.message || "");
        if (message.includes("UNIQUE constraint failed") || message.includes("users.email")) {
          return json({ ok: false, error: "هذا البريد مستخدم بالفعل. اختر اسمًا آخر." }, 409);
        }
        const errorCode = "AUTH_REGISTER_" + registerStage.toUpperCase();
        console.error("[HorusAuth] Registration failed", {
          stage: registerStage,
          errorName: String(error && error.name || "Error"),
          errorMessage: message.slice(0, 300),
          errorCode
        });
        return json({
          ok: false,
          error: "حدث خطأ أثناء إنشاء الحساب. حاول مرة أخرى. (رمز: " + errorCode + ")",
          errorCode
        }, 503);
      }
    }

    if (url.pathname === "/v1/auth/login" && request.method === "POST") {
      if (!env.HORUS_USERS) return json({ ok: false, error: "Database unavailable" }, 503);
      try {
        const body = await readJson(request);
        const email = String(body.email || "").trim().toLowerCase();
        const password = String(body.password || "");
        if (!/^[a-z0-9](?:[a-z0-9._-]{1,22}[a-z0-9])@horus\.com$/.test(email) ||
            email.includes("..") || email.includes(".-") || email.includes("-." ) ||
            !password || password.length > 128) {
          return json({ ok: false, error: "البريد الإلكتروني أو كلمة المرور غير صحيحة." }, 401);
        }

        const user = await env.HORUS_USERS.prepare(
          "SELECT id, first_name, last_name, phone, email, password_hash, password_salt FROM users WHERE email = ? COLLATE NOCASE LIMIT 1"
        ).bind(email).first();

        if (!user) {
          // Perform a password hash even for unknown addresses to reduce timing-based account discovery.
          const dummySalt = new Uint8Array(16);
          await hashPassword(password, dummySalt);
          return json({ ok: false, error: "البريد الإلكتروني أو كلمة المرور غير صحيحة." }, 401);
        }

        const saltText = String(user.password_salt || "").replace(/-/g, "+").replace(/_/g, "/");
        const saltBytes = Uint8Array.from(atob(saltText + "=".repeat((4 - saltText.length % 4) % 4)), c => c.charCodeAt(0));
        const submittedHash = await hashPassword(password, saltBytes);
        const expected = String(user.password_hash || "");
        let mismatch = submittedHash.length ^ expected.length;
        const maxLength = Math.max(submittedHash.length, expected.length);
        for (let i = 0; i < maxLength; i++) {
          mismatch |= (submittedHash.charCodeAt(i) || 0) ^ (expected.charCodeAt(i) || 0);
        }
        if (mismatch !== 0) {
          return json({ ok: false, error: "البريد الإلكتروني أو كلمة المرور غير صحيحة." }, 401);
        }

        const session = await createSession(env, user.id);
        const profileImageData = env.HORUS_MAPS_CONFIG
          ? await env.HORUS_MAPS_CONFIG.get("horus:profile-image:v1:" + user.id)
          : null;
        return json({
          ok: true,
          message: "تم تسجيل الدخول بنجاح.",
          sessionToken: session.token,
          expiresAt: session.expiresAt,
          user: {
            id: user.id,
            firstName: user.first_name,
            lastName: user.last_name,
            phone: user.phone || "",
            email: user.email,
            profileImageData
          }
        });
      } catch (error) {
        console.error("[HorusAuth] Login failed", {
          errorName: String(error && error.name || "Error"),
          errorMessage: String(error && error.message || "").slice(0, 200)
        });
        return json({ ok: false, error: "تعذر تسجيل الدخول الآن. تحقق من اتصالك وحاول مجددًا." }, 503);
      }
    }

    if (url.pathname === "/v1/profile/image" && request.method === "POST") {
      if (!env.HORUS_USERS || !env.HORUS_MAPS_CONFIG) {
        return json({ ok: false, error: "خدمة حفظ الصور غير متاحة حاليًا." }, 503);
      }
      try {
        const user = await getAuthenticatedUser(request, env);
        if (!user) return json({ ok: false, error: "انتهت صلاحية الجلسة. سجل الدخول مرة أخرى." }, 401);
        const raw = await request.text();
        if (raw.length > 400000) return json({ ok: false, error: "الصورة كبيرة جدًا. اختر صورة أصغر." }, 413);
        const body = JSON.parse(raw);
        const imageBase64 = String(body.imageBase64 || "");
        if (!/^[A-Za-z0-9+/]+={0,2}$/.test(imageBase64) || imageBase64.length < 100) {
          return json({ ok: false, error: "ملف الصورة غير صالح." }, 400);
        }
        await env.HORUS_MAPS_CONFIG.put(
          "horus:profile-image:v1:" + user.id,
          "data:image/jpeg;base64," + imageBase64
        );
        return json({ ok: true, message: "تم حفظ صورة الحساب." });
      } catch (error) {
        console.error("[HorusProfile] Image upload failed", String(error && error.message || "").slice(0, 200));
        return json({ ok: false, error: "تعذر حفظ صورة الملف الشخصي الآن." }, 503);
      }
    }

    if (url.pathname === "/v1/auth/me" && request.method === "POST") {
      if (!env.HORUS_USERS) return json({ ok: false, error: "Database unavailable" }, 503);
      try {
        const user = await getAuthenticatedUser(request, env);
        if (!user) return json({ ok: false, error: "انتهت صلاحية الجلسة. سجل الدخول مرة أخرى." }, 401);
        const profileImageData = env.HORUS_MAPS_CONFIG
          ? await env.HORUS_MAPS_CONFIG.get("horus:profile-image:v1:" + user.id)
          : null;
        return json({ ok: true, user: {
          id: user.id, firstName: user.first_name, lastName: user.last_name,
          phone: user.phone || "", email: user.email, profileImageData
        }});
      } catch (error) {
        console.error("[HorusAuth] Session lookup failed", String(error && error.message || "").slice(0, 200));
        return json({ ok: false, error: "تعذر التحقق من الجلسة الآن." }, 503);
      }
    }

    if (url.pathname === "/v1/auth/logout" && request.method === "POST") {
      if (!env.HORUS_USERS) return json({ ok: false, error: "Database unavailable" }, 503);
      try {
        const authorization = request.headers.get("Authorization") || "";
        const match = authorization.match(/^Bearer ([A-Za-z0-9_-]{40,100})$/);
        if (!match) return json({ ok: true, message: "تم تسجيل الخروج." });
        const tokenHash = await sha256Base64Url(match[1]);
        await env.HORUS_USERS.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(tokenHash).run();
        return json({ ok: true, message: "تم تسجيل الخروج." });
      } catch (error) {
        console.error("[HorusAuth] Logout failed", String(error && error.message || "").slice(0, 200));
        return json({ ok: false, error: "تعذر تسجيل الخروج من الخادم." }, 503);
      }
    }

    if (url.pathname === "/v1/auth/delete-account" && request.method === "POST") {
      if (!env.HORUS_USERS) return json({ ok: false, error: "Database unavailable" }, 503);
      try {
        const user = await getAuthenticatedUser(request, env);
        if (!user) return json({ ok: false, error: "انتهت صلاحية الجلسة. سجل الدخول مرة أخرى." }, 401);
        const body = await readJson(request);
        const password = String(body.password || "");
        if (!password || password.length > 128) {
          return json({ ok: false, error: "أدخل كلمة المرور لتأكيد حذف الحساب." }, 400);
        }
        const saltText = String(user.password_salt || "").replace(/-/g, "+").replace(/_/g, "/");
        const saltBytes = Uint8Array.from(atob(saltText + "=".repeat((4 - saltText.length % 4) % 4)), c => c.charCodeAt(0));
        const submittedHash = await hashPassword(password, saltBytes);
        const expected = String(user.password_hash || "");
        let mismatch = submittedHash.length ^ expected.length;
        const maxLength = Math.max(submittedHash.length, expected.length);
        for (let i = 0; i < maxLength; i++) {
          mismatch |= (submittedHash.charCodeAt(i) || 0) ^ (expected.charCodeAt(i) || 0);
        }
        if (mismatch !== 0) return json({ ok: false, error: "كلمة المرور غير صحيحة. لم يتم حذف الحساب." }, 401);
        await env.HORUS_USERS.prepare("DELETE FROM users WHERE id = ?").bind(user.id).run();
        // Verify persisted state rather than relying on the D1 changes counter.
        const remainingUser = await env.HORUS_USERS.prepare(
          "SELECT id FROM users WHERE id = ? LIMIT 1"
        ).bind(user.id).first();
        if (remainingUser) {
          return json({ ok: false, error: "تعذر حذف الحساب. لم يتم تأكيد الحذف." }, 500);
        }
        if (env.HORUS_MAPS_CONFIG) {
          try {
            await env.HORUS_MAPS_CONFIG.delete("horus:profile-image:v1:" + user.id);
          } catch (imageCleanupError) {
            console.error("[HorusProfile] Deleted account but profile image cleanup failed", String(imageCleanupError && imageCleanupError.message || "").slice(0, 160));
          }
        }
        return json({ ok: true, message: "تم حذف الحساب نهائيًا." });
      } catch (error) {
        console.error("[HorusAuth] Account deletion failed", String(error && error.message || "").slice(0, 200));
        return json({ ok: false, error: "تعذر حذف الحساب الآن." }, 503);
      }
    }

    if (request.method !== "GET" && request.method !== "POST") {
      return json({ ok: false, error: "Method not allowed" }, 405, { Allow: "GET, POST, OPTIONS" });
    }

    return json({ ok: false, error: "Not found" }, 404);
  }
};
