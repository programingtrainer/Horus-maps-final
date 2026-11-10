# HorusMaps flight search engine

## Current implementation

The Worker endpoint `POST /v1/search/flights` accepts the origin and destination coordinates plus a travel date.

1. It discovers nearby airports from OpenStreetMap Overpass results that contain a valid IATA code.
2. It selects the nearest distinct origin and destination airports.
3. It requests the published route schedule from Navo24's official Flight Schedules API (`https://api.navo24.com/v1/flight/flights`). Requests outside the documented HKG–FRA demo route require a `tmcp_` bearer key stored as the `NAVO24_API_KEY` Cloudflare Worker secret.
4. It normalizes flight number, airline, airports, scheduled departure/arrival, stops, status, source update time, and duration.
5. It caches the response in the existing `HORUS_MAPS_CONFIG` KV namespace for three hours.

The Android client displays the returned schedule and continues to use the existing road-navigation handoff to/from airports.

## Data honesty

- Navo24 is a beta flight-schedule source. Coverage is incomplete and a route may not be available even when both airports are known.
- The free schedule feed does not provide ticket prices or booking inventory. The app explicitly labels prices as unavailable; it must not infer or fabricate fares.
- Schedule data is not a guarantee that a seat is available or that the flight will operate as planned. Users should confirm with the airline before travel.
- Airport discovery depends on IATA tags being present in OpenStreetMap.

## Free-tier constraints

Navo24's current Flight Schedules API reference states:
- HKG ↔ FRA is the documented free demo route and works without a key.
- Other flight-search routes require a `tmcp_` API key sent as `Authorization: Bearer ...`.
- Airport, airline, and coverage reference lookups are free and do not require a key.
- The schedule feed is refreshed about every three hours and provides schedules for the coming seven days.
- The public flight API reference does not establish a guaranteed free monthly allowance for arbitrary route searches. Do not assume a generated key makes all searches free; verify the account's access and pricing before configuring it.

The Worker caches identical coordinate/date searches for three hours to reduce duplicate requests. This cache is a cost/rate-limit optimization, not a way to bypass the provider's quota.

## Sources and licensing

- Navo24 Flight Schedules API reference: https://navo24.com/developers/reference/flight/\n- Navo24 route-search endpoint: https://navo24.com/developers/reference/flight/flights/
- OpenStreetMap data: https://www.openstreetmap.org/copyright

OpenStreetMap data is provided under the ODbL. Keep attribution visible in the product and review ODbL obligations before distributing derived databases.

## Not yet included

- Live ticket prices and bookable inventory.\n- A configured `NAVO24_API_KEY` for non-demo routes.
- Multi-airport search beyond the nearest airport pair.
- Airline-direct schedule adapters.
- Automatic source health monitoring and provider fallback.
- Production deployment verification.

These features should be added only when a source provides documented access and terms that permit HorusMaps' intended use.
