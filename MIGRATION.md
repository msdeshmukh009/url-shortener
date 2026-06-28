# Migration Guide: v1.x → v2.0

## Overview

In v2.0, we introduced **API key authentication** for endpoints that create,
modify, or list URLs. Public redirect endpoints remain unauthenticated. This 
is a **breaking change** for clients that create or manage URLs — your 
existing integrations will stop working until you update them.

## Why this change?

- Track per-user URL ownership and statistics
- Prevent abuse and enforce rate limits per user
- Enable tier-based features (bulk shorten, password protection)
- Comply with industry security practices

## Timeline

| Date | Event |
|---|---|
| 2026-06-03 | v2.0 released |
| 2026-08-15 | v1.x deprecated (2 months grace period) |
| 2026-12-15 | v1.x endpoints removed |

## What changed

The following endpoints now require an `X-API-Key` header. Missing or 
invalid keys return `400 Bad Request` (missing) or `401 Unauthorized` 
(invalid).

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/shorten` | Create a short URL |
| POST | `/api/shorten/bulk` | Bulk-create short URLs |
| PATCH | `/api/shorten/{shortCode}` | Update a short URL |
| DELETE | `/api/urls/{shortCode}` | Soft-delete a short URL |
| GET | `/api/urls` | List your short URLs |

## Step-by-step migration

### Step 1: Get an API key

Contact the sales team to receive your API key.

### Step 2: Update your client code

#### Before (v1.x)
```bash
curl -X POST https://your-app.onrender.com/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com"}'
```

#### After (v2.0)
```bash
curl -X POST https://your-app.onrender.com/api/shorten \
  -H "Content-Type: application/json" \
  -H "X-API-Key: abc123-your-new-key" \
  -d '{"originalUrl": "https://example.com"}'
```

## What hasn't changed

These endpoints remain public — no API key required:

- `GET /api/redirect?shortCode={code}` — JSON-based redirect lookup
- `GET /r/{shortCode}` — HTML redirect endpoint (browser-facing)
- `POST /r/{shortCode}/unlock` — password verification for protected URLs
- `GET /actuator/health` — health check
- `GET /actuator/health/liveness` — Kubernetes-style liveness probe
- `GET /actuator/health/readiness` — Kubernetes-style readiness probe

These endpoints are designed for end-users clicking shortened links and 
require no authentication.