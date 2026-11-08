#!/usr/bin/env python3
"""Build accurate continent polygons from Natural Earth 1:50m country data.

The output is a seven-feature GeoJSON used only for continent overview outlines.
Natural Earth is public-domain data. Country geometries are dissolved by continent,
with conventional transcontinental splits for Russia, Kazakhstan, Turkey and Egypt.
"""
from __future__ import annotations

import json
import os
import urllib.request
from collections import defaultdict
from pathlib import Path

from shapely.geometry import LineString, shape, mapping
from shapely.ops import split, unary_union
from shapely.validation import make_valid

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app" / "src" / "main" / "assets" / "continents.geojson"
SOURCE_URL = (
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/"
    "master/geojson/ne_50m_admin_0_countries.geojson"
)
CACHE = ROOT / "build" / "ne_50m_admin_0_countries.geojson"

# Approximate Europe/Asia divide along the Urals and Ural River.
URALS = [
    (81.0, 69.5), (69.0, 69.5), (68.8, 66.5), (66.5, 64.5),
    (62.0, 59.5), (58.0, 59.2), (55.0, 59.2), (52.0, 58.5),
    (51.8, 55.1), (51.2, 51.4), (47.0, 51.9), (40.0, 51.9),
]
# Coordinates are (longitude, latitude).
URAL_LINE = LineString([(lon, lat) for lon, lat in URALS])
TURKEY_DIVIDE = LineString([
    (26.0, 40.20), (26.55, 40.25), (27.00, 40.35),
    (27.45, 40.55), (27.95, 40.70), (28.45, 40.85),
    (28.90, 41.05), (29.05, 41.25), (29.15, 41.55),
    (29.25, 41.90),
])
EGYPT_DIVIDE = LineString([
    (32.35, 31.30), (32.35, 30.90), (32.35, 30.50),
    (32.35, 30.10), (32.35, 29.70), (32.35, 29.30),
    (32.35, 28.90), (32.35, 28.50),
])

LABELS = {
    "Africa": ("أفريقيا", "AFRICA", 20, 2),
    "Asia": ("آسيا", "ASIA", 91, 47),
    "Europe": ("أوروبا", "EUROPE", 17, 53),
    "North America": ("أمريكا الشمالية", "NORTH AMERICA", -102, 43),
    "South America": ("أمريكا الجنوبية", "SOUTH AMERICA", -61, -17),
    "Oceania": ("أوقيانوسيا", "OCEANIA", 135, -24),
    "Antarctica": ("القارة القطبية الجنوبية", "ANTARCTICA", 0, -78),
}
ALLOWED = set(LABELS)


def download_source() -> Path:
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    if CACHE.exists() and CACHE.stat().st_size > 100_000:
        return CACHE
    request = urllib.request.Request(
        SOURCE_URL,
        headers={"User-Agent": "HorusMaps-continent-data-builder/1.0"},
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()
    if len(data) < 100_000:
        raise RuntimeError(f"Natural Earth download looks incomplete: {len(data)} bytes")
    CACHE.write_bytes(data)
    return CACHE


def polygons(geometry):
    if geometry.geom_type == "Polygon":
        return [geometry]
    if geometry.geom_type == "MultiPolygon":
        return list(geometry.geoms)
    if geometry.geom_type == "GeometryCollection":
        return [part for part in geometry.geoms if part.geom_type == "Polygon"]
    return []


def split_lines(code: str):
    if code in ("RUS", "KAZ"):
        return [URAL_LINE]
    if code == "TUR":
        return [TURKEY_DIVIDE]
    if code == "EGY":
        return [EGYPT_DIVIDE]
    return []


def classify(code: str, natural_continent: str, lon: float, lat: float):
    if code == "RUS":
        # Kaliningrad is west of the 0-degree meridian and remains European.
        boundary_lon = next(
            (lo for (la0, lo0), (la1, lo1) in zip(URALS, URALS[1:])
             if la1 <= lat <= la0
             for lo in [lo0 + (lo1 - lo0) * (la0 - lat) / (la0 - la1)]),
            69.5 if lat > 81 else 51.9,
        )
        return "Asia" if lon > boundary_lon and lon >= 0 else "Europe"
    if code == "KAZ":
        boundary_lon = next(
            (lo for (la0, lo0), (la1, lo1) in zip(URALS, URALS[1:])
             if la1 <= lat <= la0
             for lo in [lo0 + (lo1 - lo0) * (la0 - lat) / (la0 - la1)]),
            51.9,
        )
        return "Europe" if lon < boundary_lon else "Asia"
    if code == "TUR":
        return "Europe" if lon < 29.1 and lat > 40.6 else "Asia"
    if code == "EGY":
        return "Asia" if lon > 32.35 else "Africa"

    # Put overseas territories of European countries in their geographic continent.
    if natural_continent == "Europe" and (lon < -30 or lat < 34):
        if lon < -30:
            return "South America" if lat < 13 else "North America"
        return "Africa"
    return natural_continent if natural_continent in ALLOWED else None


def split_country_piece(piece, code):
    result = [piece]
    for line in split_lines(code):
        next_result = []
        for geom in result:
            try:
                next_result.extend(polygons(split(geom, line)))
            except Exception:
                next_result.append(geom)
        result = next_result
    return result


def main():
    source_path = download_source()
    source = json.loads(source_path.read_text(encoding="utf-8"))
    grouped = defaultdict(list)
    seen = set()

    for feature in source.get("features", []):
        props = feature.get("properties") or {}
        code = str(props.get("ADM0_A3") or props.get("SOV_A3") or "")
        natural_continent = str(props.get("CONTINENT") or "")
        if not feature.get("geometry"):
            continue

        geom = make_valid(shape(feature["geometry"]))
        for country_polygon in polygons(geom):
            for piece in split_country_piece(country_polygon, code):
                point = piece.representative_point()
                continent = classify(code, natural_continent, point.x, point.y)
                if continent:
                    grouped[continent].append(piece)

    output_features = []
    for continent, (name_ar, name_en, label_lon, label_lat) in LABELS.items():
        parts = grouped.get(continent, [])
        if not parts:
            raise RuntimeError(f"No geometry generated for {continent}")
        merged = make_valid(unary_union(parts))
        # Keep the 1:50m coastline character while removing sub-pixel vertices.
        merged = merged.simplify(0.006, preserve_topology=True)
        if merged.is_empty:
            raise RuntimeError(f"Empty geometry generated for {continent}")
        output_features.append({
            "type": "Feature",
            "properties": {
                "continent": continent,
                "name_en": name_en,
                "name_ar": name_ar,
                "label_lon": label_lon,
                "label_lat": label_lat,
                "source": "Natural Earth 1:50m Admin 0 Countries",
                "source_version": "5.1.1",
            },
            "geometry": mapping(merged),
        })
        seen.add(continent)

    if seen != ALLOWED:
        raise RuntimeError(f"Missing continents: {sorted(ALLOWED - seen)}")

    result = {
        "type": "FeatureCollection",
        "name": "horus_continents_natural_earth_50m",
        "features": output_features,
    }
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_text(
        json.dumps(result, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(
        f"Generated {ASSET} with {len(output_features)} continents "
        f"from Natural Earth 1:50m data ({ASSET.stat().st_size:,} bytes)"
    )


if __name__ == "__main__":
    main()
