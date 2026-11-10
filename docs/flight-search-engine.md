# HorusMaps flight search engine

## Current implementation

The Worker endpoint `POST /v1/search/flights` accepts origin/destination coordinates and a travel date.

1. It discovers nearby airports from OpenStreetMap Overpass results that contain a valid IATA code.
2. It selects the nearest distinct origin and destination airports.
3. It requests passenger flight instances from the OAG Flight Info API.
4. It normalizes flight number, airline, airports, scheduled departure/arrival, duration and status.
5. It caches the response in the existing `HORUS_MAPS_CONFIG` KV namespace for three hours.

The Android client displays the returned schedule and continues to use the existing road-navigation handoff to/from airports.

## Provider access and free allowance

OAG's developer portal advertises a free tier with:
- Flight Info: 1,000 API calls per 30 days, up to 10 results per response.
- Flight Info Connections: 1,000 calls per 30 days.
- Master Data: 100 calls per 30 days.
- No credit card is required to start exploring the API.

Create an OAG Developer Portal account, confirm the email, subscribe to the free Flight Info product, and copy its subscription key. Store it only as the Cloudflare Worker secret `OAG_SUBSCRIPTION_KEY`. The API request must be made server-side using the `Subscription-Key` header; never place this key in Android or GitHub.

OAG's published free limits reset every 30 days from the date the subscription was created. The three-hour Worker cache reduces duplicate requests but does not increase the provider's allowance.

## Data honesty and limitations

- OAG Flight Info provides scheduled flight information and operational status, not ticket prices or bookable inventory.
- OAG states that airfare data is a separate product from the Developer Portal. Do not invent or estimate a ticket fare and present it as a quote.
- A schedule record is not a guarantee that a seat is available or that a flight will operate exactly as planned. Users should confirm with the airline before travel.
- The first implementation searches the nearest airport pair and direct flights. Multi-airport expansion and connecting itineraries are separate follow-up work.
- Airport discovery depends on IATA tags being present in OpenStreetMap.

## Sources and licensing

- OAG Developer Portal and free-tier details: https://developers.oag.com/
- OAG Flight Info API: https://developers.oag.com/
- OAG account creation guide: https://knowledge.oag.com/docs/flight-info-api-creating-an-account
- OpenStreetMap data: https://www.openstreetmap.org/copyright

OpenStreetMap data is provided under the ODbL. Keep attribution visible in the product and review ODbL obligations before distributing derived databases.

## Not yet included

- Live ticket prices and bookable inventory.
- Connecting itineraries through the OAG Connections API.
- Multi-airport city-code expansion.
- Airline-direct schedule adapters.
- Automatic source health monitoring and provider fallback.
- Production deployment verification.
