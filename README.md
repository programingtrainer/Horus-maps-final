# Horus Maps

A clean Android maps application built with Kotlin, Jetpack Compose, and MapLibre.

## Project principles

- The map must render before optional features are initialized.
- The initial camera follows the best available device location; it must never default to a hard-coded city.
- Location permission is requested in context and denied states remain usable.
- Search, places, routing, and backend integrations are isolated behind clear interfaces.
- Production Cloudflare resources are never changed without review and explicit approval.

## Initial milestone

Create a stable Android shell and render the OpenFreeMap Liberty style with MapLibre. Then add location, search, nearby places, and routing in separate tested milestones.

## Backend

The Cloudflare Worker horus-maps-final is reserved for the project. Its current production configuration is being reviewed; do not deploy changes until the API contract and tests are in place.
