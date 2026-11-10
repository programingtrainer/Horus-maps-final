# HorusMaps flight search engine

## Current implementation

The Worker endpoint `POST /v1/search/flights` accepts origin/destination coordinates and a travel date.

1. It discovers nearby airports from OpenStreetMap Overpass results that contain a valid IATA code.
2. It selects the nearest distinct origin and destination airports.
3. It requests passenger flight instances from the OAG Flight Info API.
4. It normalizes flight number, airline, airports, scheduled departure/arrival, duration and status.
5. It optionally requests recent cached fare observations from the Aviasales Data API (Travelpayouts).
6. It caches schedule responses for three hours and fare observations for 24 hours in the existing `HORUS_MAPS_CONFIG` KV namespace.

The Android client displays schedules and recent fare observations separately. Fare observations are not asserted to belong to the exact same flight instance as an OAG schedule unless the identifiers match.

## Ignav fare and itinerary provider

When the Worker secret `IGNAV_API_KEY` is configured, Ignav is the primary source for flight itineraries, fare observations, and booking handoffs.

- The Worker calls `POST /api/fares/one-way` server-side with the nearest OSM-discovered IATA airport pair, travel date, economy cabin, and Saudi market (`SA`).
- Fare results are cached in the Worker KV for 30 minutes. This matches Ignav's stated reuse window and avoids spending a request for repeated identical searches.
- Each itinerary is normalized into the existing HorusMaps flight result model. The fare amount, currency, carrier, first flight number, stops, local departure/arrival times, and provider observation timestamp are kept separate from the road route.
- Booking links are fetched only when the user taps **روابط الحجز**. The Worker calls `POST /api/fares/booking-links` with the itinerary's `ignav_id` and caches the resulting links for five minutes. This avoids spending one additional request for every result before the user chooses one.
- The Android app never receives the API key. It calls only the HorusMaps Worker.

Set `IGNAV_API_KEY` as a Cloudflare Worker secret for the Preview environment used by this branch. Never put the key in GitHub, `wrangler.toml`, Android resources, or BuildConfig. Do not set it on Production until the user explicitly approves a production rollout.

Ignav advertises 1,000 free successful requests as a one-time allowance, then USD $2 per 1,000 successful requests. The allowance does not reset monthly. Fare results may be reused for up to 30 minutes, and prices/availability can change; the app must tell users to confirm the final amount with the booking provider.

If `IGNAV_API_KEY` is absent, the existing OAG + Travelpayouts integration remains as a legacy fallback. If an Ignav key is configured but the provider rejects the request, HorusMaps reports the provider error rather than silently switching providers and consuming additional quota.

## OAG schedule provider

OAG's developer portal advertises a free tier with:
- Flight Info: 1,000 API calls per 30 days, up to 10 results per response.
- Flight Info Connections: 1,000 calls per 30 days.
- Master Data: 100 calls per 30 days.
- No credit card is required to start exploring the API.

Create an OAG Developer Portal account, confirm the email, subscribe to the free Flight Info product, and copy its subscription key. Store it only as the Cloudflare Worker secret `OAG_SUBSCRIPTION_KEY`. The API request is made server-side; never place this key in Android or GitHub.

OAG's published free limits reset every 30 days from the date the subscription was created. The three-hour Worker cache reduces duplicate requests but does not increase the provider's allowance.

## Travelpayouts / Aviasales fare observations

Travelpayouts says joining its partner platform is free. The Aviasales Data API provides prices from its search-history cache; the specific-date endpoint returns prices found by Aviasales users in the last 48 hours, while the underlying cache is retained for up to seven days depending on query type.

Create a Travelpayouts account, create a project for HorusMaps, connect the Aviasales program, and obtain the Data API token from the profile. Store it only as the Cloudflare Worker secret `TRAVELPAYOUTS_API_TOKEN`.

The integration uses the Data API endpoint `/aviasales/v3/prices_for_dates`, not the real-time Flight Search API. Cached fares may be stale, unavailable for a route/date, or no longer bookable. HorusMaps displays the observation time where provided and labels these as indicative prices. The Worker caches identical route/date fare lookups for 24 hours, as recommended by Travelpayouts.

The real-time Aviasales Flight Search API has a 50,000 monthly active user eligibility requirement; it is not used by this project.

## Data honesty and limitations

- Ignav returns fare itineraries and booking handoffs, but HorusMaps does not guarantee that a displayed price or seat remains available at checkout.
- OAG Flight Info provides schedules and status, not ticket prices or bookable inventory.
- Travelpayouts fare observations are cached prices from prior user searches, not guaranteed live quotes.
- A schedule or cached fare is not a guarantee that a seat is available or that a flight will operate exactly as planned. Users must verify the final price and availability with the booking provider before payment.
- The first implementation searches the nearest airport pair and direct OAG flight instances. Multi-airport expansion and connecting itineraries are separate follow-up work.
- Airport discovery depends on IATA tags being present in OpenStreetMap.

## Sources and licensing

- OAG Developer Portal and free-tier details: https://developers.oag.com/
- OAG Flight Explorer guide: https://developers.oag.com/guides/flight-explorer
- OAG account creation guide: https://knowledge.oag.com/docs/flight-info-api-creating-an-account
- Travelpayouts Aviasales Data API: https://support.travelpayouts.com/hc/en-us/articles/203956163-Aviasales-Data-API
- Travelpayouts account signup: https://support.travelpayouts.com/hc/en-us/articles/11395179019538-How-to-join-Travelpayouts
- OpenStreetMap data: https://www.openstreetmap.org/copyright

OpenStreetMap data is provided under the ODbL. Keep attribution visible in the product and review ODbL obligations before distributing derived databases.

## Not yet included

- Guaranteed live ticket prices and bookable inventory.
- Round-trip and multi-city booking flow in the Android interface; the first Ignav integration is one-way.
- Multi-airport pair expansion when the nearest airport has no offers.
- OAG connecting itineraries through the Connections API.
- Multi-airport city-code expansion.
- Airline-direct schedule adapters.
- Automatic source health monitoring and provider fallback.
- Production deployment verification.
