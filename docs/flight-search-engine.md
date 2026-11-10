# HorusMaps flight search engine

## Current implementation

The Worker endpoint `POST /v1/search/flights` accepts the origin and destination coordinates plus a travel date.

1. It discovers nearby airports from OpenStreetMap Overpass results that contain a valid IATA code.
2. It selects the nearest distinct origin and destination airports.
3. It requests the published route schedule from Navo24.
4. It normalizes flight number, airline, airports, scheduled departure/arrival, stops, status, source update time, and duration.
5. It caches the response in the existing `HORUS_MAPS_CONFIG` KV namespace for three hours.

The Android client displays the returned schedule and continues to use the existing road-navigation handoff to/from airports.

## Data honesty

- Navo24 is a beta flight-schedule source. Coverage is incomplete and a route may not be available even when both airports are known.
- The free schedule feed does not provide ticket prices or booking inventory. The app explicitly labels prices as unavailable; it must not infer or fabricate fares.
- Schedule data is not a guarantee that a seat is available or that the flight will operate as planned. Users should confirm with the airline before travel.
- Airport discovery depends on IATA tags being present in OpenStreetMap.

## Free-tier constraints

Navo24's published documentation currently states:
- One search without an account.
- A free Navo account allows three searches per day and ten searches per month.
- Airport, airline, and coverage lookups are free.
- The schedule feed is refreshed about every three hours and covers yesterday through seven days ahead.

The Worker caches identical coordinate/date searches for three hours to reduce duplicate requests. This cache is a cost/rate-limit optimization, not a way to bypass the provider's quota.

## Sources and licensing

- Navo24 schedule API documentation: https://flight.navo24.com/docs
- OpenStreetMap data: https://www.openstreetmap.org/copyright

OpenStreetMap data is provided under the ODbL. Keep attribution visible in the product and review ODbL obligations before distributing derived databases.

## Not yet included

- Live ticket prices and bookable inventory.
- Multi-airport search beyond the nearest airport pair.
- Airline-direct schedule adapters.
- Automatic source health monitoring and provider fallback.
- Production deployment verification.

These features should be added only when a source provides documented access and terms that permit HorusMaps' intended use.
