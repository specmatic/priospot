# PrioSpot Docker Usage

This document covers running PrioSpot via Docker.

For complete CLI usage, command options, and non-Docker examples, see the main README:
- https://github.com/specmatic/priospot#readme

## Prerequisites

- Docker installed and running
- A repository with source + coverage + complexity reports available inside the mounted workspace

## Pull Image

Use the image/tag published with a release (see releases page):
- https://github.com/specmatic/priospot/releases

Example:

```bash
docker pull specmatic/priospot
```

## Run Analyze

```bash
docker run --rm \
  -v "$PWD:/usr/src/app" \
  specmatic/priospot \
  analyze \
  --project-name sample \
  --source-roots src/main/kotlin \
  --coverage-report build/reports/kover/report.xml \
  --complexity-report build/reports/detekt/detekt.xml \
  --output-json build/reports/priospot/priospot.json
```

## Run Report

```bash
docker run --rm \
  -v "$PWD:/usr/src/app" \
  specmatic/priospot \
  report \
  --input-json build/reports/priospot/priospot.json \
  --type priospot \
  --output-svg build/reports/priospot/priospot-interactive-treemap.svg
```

## Notes

- Paths passed to CLI options are relative to the working directory (`/usr/src/app` in the examples).
- For all supported flags and behavior details, refer to the GitHub README.
