# OpenAPI diff: `haven-api-docs-1.0.1.yaml` → `haven-api-docs-1.0.2.yaml`

Companion to [`integration-log.md`](./integration-log.md) (Haven changelog + matrix). Use this file when something **changed in the contract** but is **not** spelled out in the integration log prose.

## Summary

- **1.0.2** adds many **new paths** (auth recovery, Dream AI stub, leads, moderation, platform/ads admin, avatar & agent marketing, listing videos, inspection owner/agent actions, etc.).
- **1.0.2** extends **existing schemas** (listings, properties, profiles, inspections) with fields and enums that **do not appear as new paths** — Vista must diff components/schemas, not only paths.

## New paths (present in 1.0.2, absent in 1.0.1)

| Path | Notes |
| --- | --- |
| `/auth/forgot-password` | POST → 202 + `ForgotPasswordResponse` (anti-enumeration; optional `debugResetToken` in dev) |
| `/auth/reset-password` | POST → 204; body `token` + `newPassword` |
| `/dream-ai/suggestions` | POST stub; **requires Bearer**; returns `listingIds[]` |
| `/me/avatar` | POST multipart profile image |
| `/me/agent-marketing` | GET list / POST upload |
| `/me/agent-marketing/{id}` | DELETE |
| `/me/agent-marketing/order` | PATCH reorder |
| `/me/ad-campaigns` | GET (pageable) / POST create draft |
| `/me/ad-campaigns/{id}` | PATCH sponsor fields |
| `/listings/{listingId}/leads` | GET (owner, paginated) / POST (applicant submit) |
| `/listings/{listingId}/leads/{leadId}/reveal` | POST (owner reveal PII) |
| `/listings/{listingId}/comments/{commentId}/flag` | POST authenticated flag |
| `/listings/{listingId}/videos` | GET public / POST owner add video URL |
| `/listings/videos/{videoId}` | DELETE (pattern in spec) |
| `/inspections/{id}/owner/approve` | POST |
| `/inspections/{id}/owner/decline` | POST |
| `/inspections/{id}/mark-no-show` | POST |
| `/inspections/{id}/agent/complete` | POST |
| `/inspections/{id}/agent/reschedule` | POST + body |
| `/inspections/{id}/agent/extras` | PATCH |
| `/admin/platform-settings` | GET / PATCH shallow merge |
| `/admin/comment-flags` | GET queue |
| `/admin/comment-flags/{id}/resolve` | POST |
| `/admin/comment-flags/{id}/dismiss` | POST |
| `/admin/listings` | GET catalogue (paginated, `status`) |
| `/admin/listings/{id}/moderation-snapshot` | GET takedown/moderation context |
| `/admin/listings/{id}/leads` | GET admin full PII |
| `/admin/ad-campaigns` | GET |
| `/admin/ad-campaigns/{id}` | PATCH admin review |

## Same path, new or changed behaviour (easy to miss in changelog)

| Topic | Change |
| --- | --- |
| **`/properties/{id}`** | **PATCH** added (1.0.1 had **GET** only). Partial update: address, bedrooms, bathrooms, `sizeSqm`, description, lat/lng pair; property **type** immutable. |
| **`/listings/{listingId}/slots` POST** | Authorisation text: **owner or accepted assigned agent** may create slots (1.0.1 doc said owner-only). |
| **`InspectionResponse.status`** | Enum adds **`NO_SHOW`**, **`COMPLETED`** (and possibly `agentExtras` string field). Vista `InspectionResponse` types must allow these or UI/state mapping breaks. |
| **`ListingResponse` / create listing** | `virtualTourUrl`, `priceNegotiable`, `petsAllowed`, `utilitiesNote`, `floorPlanUrl`, `ownerPublicBio` (and related create/update bodies). |
| **`PropertySummary`** | Optional **`latitude`** / **`longitude`** (WGS-84; send both or neither on create/patch). |
| **`/me` GET** | Documented for **login shell**: 200 = still signed in. |
| **`/me` DELETE** | Soft-delete account (1.0.1 may lack this — confirm runtime). |
| **`/me` PATCH** (`UpdateMyProfileRequest`) | **`publicBio`**, **`profileImageUrl`**, **`notificationPreferences`** (JSON string). |
| **Tag descriptions** | 1.0.2 Inspections tag text still mentions legacy “owner approve not yet backend” in one place while paths **exist** — treat **paths + schemas** as truth over prose drift. |
| **Example media URLs** | `media.dreamhomes.today` → `media.dreamhomes.com` in examples only. |

## Vista follow-ups (not all wired in product UI yet)

- **Applicant** `POST /listings/{id}/leads` on listing detail if product wants “express interest” vs comments-only.
- **Admin** pages: migrate `localStorage` queues (comment flags, ads, platform) to REST where UX allows.
- **Agent** marketing gallery + order PATCH; **avatar** upload on profile flows.
- **Listing videos** vs **`virtualTourUrl`** — two mechanisms; product should pick primary UX.

When in doubt, diff **`components/schemas`** sections between the two YAML files or export fresh from Haven `GET /v3/api-docs`.
