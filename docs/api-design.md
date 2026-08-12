# API Design

## Overview

The application exposes a versioned JSON REST API under `/api/v1`. It uses feature-oriented packages with a consistent flow:

```text
HTTP request
    -> feature/api controller
    -> feature/application service and transaction boundary
    -> feature/persistence repository
    -> JPA domain model
    -> PostgreSQL 18
```

Controllers accept validation-focused request records and return response records rather than exposing JPA entities. Business transitions live in application/domain code, while database constraints provide a final concurrency and integrity boundary.

## Interactive documentation

When the application is running:

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

Both documentation endpoints are public. The generated OpenAPI definition describes an HTTP bearer scheme named `bearerAuth`.

## Authentication and authorization

### Token model

- Login returns a short-lived JWT access token.
- Tokens are signed and verified with `HS256` using one Base64-encoded symmetric HMAC secret containing at least 256 bits.
- The configured issuer is written to `iss` and validated on incoming tokens.
- The token subject, `sub`, is the normalized user email.
- The custom `roles` claim contains `OWNER` and/or `ADMIN`.
- The API is stateless; clients send `Authorization: Bearer <token>` on secured requests.

### Access boundaries

| Boundary | Access |
|---|---|
| `POST /api/v1/auth/register` | Public |
| `POST /api/v1/auth/login` | Public |
| `/api/v1/vessels/**` | OWNER |
| `/api/v1/routes/**` | OWNER |
| `/api/v1/coupons/**` | OWNER |
| `/api/v1/bookings/**` | OWNER |
| `/api/v1/dashboard` | OWNER |
| `/api/v1/admin/**` | ADMIN |
| `/api/v1/me` | Any authenticated role |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Public |

Security failures use the standard API error shape: unauthenticated access returns `401`, while a valid token without the required role returns `403`.

## Common conventions

### Content and data types

- Request and response bodies use JSON.
- Dates use ISO `YYYY-MM-DD`.
- Timestamps use ISO-8601 instants.
- Monetary values are decimal numbers backed by `numeric(12,2)`.
- Resource IDs are positive 64-bit integers.
- Enum values use uppercase names such as `PENDING_PAYMENT`.

### Resource creation

Successful creation operations return `201 Created`, a response body, and a `Location` header. State transitions and updates return `200 OK`.

### Pagination

Collection endpoints use zero-based `page` and bounded `size` parameters:

| Parameter | Default | Constraint |
|---|---:|---:|
| `page` | `0` | Must be at least 0 |
| `size` | `20` | Must be between 1 and 100 |

The standard response is:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Default ordering is deterministic: `createdAt DESC`, followed by `id DESC`.

### Ownership hiding

Owner detail operations query by both resource ID and authenticated owner ID. A missing resource and a resource belonging to another owner both return `404`, avoiding ownership disclosure.

### Error response

All handled failures use:

```json
{
  "timestamp": "2026-08-13T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/v1/example",
  "fieldErrors": [
    { "field": "exampleField", "message": "must not be blank" }
  ]
}
```

`fieldErrors` is omitted when empty.

| HTTP status | Meaning | Typical codes |
|---:|---|---|
| `400` | Malformed JSON, field validation, invalid enum/date/filter/pagination | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_REQUEST_PARAMETER` |
| `401` | Invalid login or missing/invalid bearer token | `INVALID_CREDENTIALS`, `UNAUTHENTICATED` |
| `403` | Authenticated role is not authorized | `ACCESS_DENIED` |
| `404` | Resource absent or hidden by ownership | Feature-specific `*_NOT_FOUND` |
| `409` | Duplicate resource, invalid transition, stale update, or race | Feature-specific conflict code |
| `500` | Sanitized unexpected failure | `INTERNAL_SERVER_ERROR` |

Stack traces, SQL, constraint names, tokens, hashes, and secrets are not returned.

## Endpoint catalog

### Authentication and current user

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `POST` | `/api/v1/auth/register` | Public | `RegisterOwnerRequest` | `201` | `RegisterOwnerResponse` |
| `POST` | `/api/v1/auth/login` | Public | `LoginRequest` | `200` | `AuthResponse` |
| `GET` | `/api/v1/me` | Authenticated | — | `200` | `CurrentUserResponse` |

Registration always creates an active user with the OWNER role. There is no public administrator-registration endpoint.

### Owner vessels

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `POST` | `/api/v1/vessels` | OWNER | `RegisterVesselRequest` | `201` | `VesselResponse` |
| `GET` | `/api/v1/vessels` | OWNER | Optional `status`; pagination | `200` | `PageResponse<VesselResponse>` |
| `GET` | `/api/v1/vessels/{id}` | OWNER | Owned vessel ID | `200` | `VesselResponse` |

### Admin vessel review

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `GET` | `/api/v1/admin/vessels` | ADMIN | `status` defaults to `PENDING`; pagination | `200` | `PageResponse<AdminVesselResponse>` |
| `POST` | `/api/v1/admin/vessels/{id}/approve` | ADMIN | Vessel ID | `200` | `AdminVesselResponse` |
| `POST` | `/api/v1/admin/vessels/{id}/reject` | ADMIN | `RejectVesselRequest` | `200` | `AdminVesselResponse` |

### Routes

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `GET` | `/api/v1/routes` | OWNER | Pagination | `200` | Active `PageResponse<RouteResponse>` |
| `GET` | `/api/v1/routes/{id}` | OWNER | Active route ID | `200` | `RouteResponse` |
| `POST` | `/api/v1/admin/routes` | ADMIN | `RouteRequest` | `201` | `RouteResponse` |
| `PUT` | `/api/v1/admin/routes/{id}` | ADMIN | `RouteRequest` | `200` | `RouteResponse` |
| `POST` | `/api/v1/admin/routes/{id}/activate` | ADMIN | Route ID | `200` | `RouteResponse` |
| `POST` | `/api/v1/admin/routes/{id}/deactivate` | ADMIN | Route ID | `200` | `RouteResponse` |

Current boundary: there is no `GET /api/v1/admin/routes`. An ADMIN-only account cannot list all routes, and the OWNER route collection exposes active routes only.

### Coupons and coupon payments

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `POST` | `/api/v1/admin/coupons` | ADMIN | `IssueCouponRequest` | `201` | `CouponResponse` |
| `GET` | `/api/v1/coupons` | OWNER | Optional `status`; pagination | `200` | Owned `PageResponse<CouponResponse>` |
| `POST` | `/api/v1/bookings/{bookingId}/payments/coupon` | OWNER | `CouponPaymentRequest` | `201` | `CouponPaymentResponse` |

Coupon redemption is transactional. It writes a successful `Payment`, a `CouponRedemption`, marks the coupon used, and advances the booking to pending approval.

### Owner bookings and status tracking

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `POST` | `/api/v1/bookings` | OWNER | `CreateBookingRequest` | `201` | `BookingResponse` |
| `GET` | `/api/v1/bookings` | OWNER | Optional `status`; pagination | `200` | Owned `PageResponse<BookingResponse>` |
| `GET` | `/api/v1/bookings/{id}` | OWNER | Owned booking ID | `200` | `BookingResponse` |

`BookingResponse.status` is the booking lifecycle status. Successful payment is represented as nested `payment.status`; pilot data is represented by nested `assignment`. These nested objects are `null` until applicable. Payment status is not stored on the booking table.

### Admin booking review and assignment

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `GET` | `/api/v1/admin/bookings` | ADMIN | `status` defaults to `PENDING_APPROVAL`; pagination | `200` | `PageResponse<AdminBookingResponse>` |
| `POST` | `/api/v1/admin/bookings/{id}/approve` | ADMIN | Booking ID | `200` | `AdminBookingResponse` |
| `POST` | `/api/v1/admin/bookings/{id}/reject` | ADMIN | `RejectBookingRequest` | `200` | `AdminBookingResponse` |
| `POST` | `/api/v1/admin/bookings/{id}/assign-pilot` | ADMIN | `AssignPilotRequest` | `201` | `AssignmentResponse` |
| `POST` | `/api/v1/admin/bookings/{id}/complete` | ADMIN | Booking ID | `200` | `AssignmentResponse` |

### Pilot management

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `GET` | `/api/v1/admin/pilots` | ADMIN | Optional `status`; pagination | `200` | `PageResponse<PilotResponse>` |
| `GET` | `/api/v1/admin/pilots/available` | ADMIN | Required `serviceDate`; pagination | `200` | `PageResponse<PilotResponse>` |
| `POST` | `/api/v1/admin/pilots` | ADMIN | `CreatePilotRequest` | `201` | `PilotResponse` |
| `PUT` | `/api/v1/admin/pilots/{id}` | ADMIN | `UpdatePilotRequest` including `version` | `200` | `PilotResponse` |
| `POST` | `/api/v1/admin/pilots/{id}/deactivate` | ADMIN | Pilot ID | `200` | `PilotResponse` |

Pilot update uses the returned `version` field for optimistic concurrency control.

### Reports and dashboards

| Method | Path | Access | Request / query | Success | Response |
|---|---|---|---|---:|---|
| `GET` | `/api/v1/dashboard` | OWNER | — | `200` | `OwnerDashboardResponse` |
| `GET` | `/api/v1/admin/dashboard` | ADMIN | — | `200` | `AdminDashboardResponse` |
| `GET` | `/api/v1/admin/reports/bookings` | ADMIN | Optional `status`, `from`, `to`, `routeId`, `pilotId`; pagination | `200` | `PageResponse<BookingReportRow>` |

Report filters are combinable. Service-date bounds are inclusive. `from` later than `to`, nonpositive IDs, and invalid pagination return `400`. No matches produce a `200` empty page.

## Request contracts

| Request | Fields and constraints |
|---|---|
| `RegisterOwnerRequest` | `fullName` required, max 150; `email` required/valid, max 255; optional `phone` length 7–50; `password` length 8–72 |
| `LoginRequest` | Valid required `email`, max 255; required `password`, max 72 |
| `RegisterVesselRequest` | Required `name` max 150; unique `registrationNumber` max 100; required `vesselType` max 80 |
| `RejectVesselRequest` | Nonblank `reason`, max 2000 |
| `RouteRequest` | Unique `code` max 30; required `name` max 150; required `origin`/`destination` max 100; positive `serviceFee`, max two decimals |
| `IssueCouponRequest` | Positive `ownerId`; positive `amount`, max two decimals; future `expiresAt` |
| `CreateBookingRequest` | Positive `vesselId` and `routeId`; future `serviceDate` |
| `CouponPaymentRequest` | Nonblank `couponCode`, max 80 |
| `RejectBookingRequest` | Nonblank `reason`, max 1000 |
| `CreatePilotRequest` | Unique `employeeNumber` max 80; required `name` max 150; optional `phone` max 50; optional valid `email` max 255 |
| `UpdatePilotRequest` | Same profile fields as creation plus nonnegative required optimistic-lock `version` |
| `AssignPilotRequest` | Positive required `pilotId` |

## Important response design choices

- Password hashes are never serialized.
- Owner responses omit administrative review metadata; admin response variants include reviewer identity, timestamps, and rejection reason.
- Booking responses embed vessel and route summaries to preserve a convenient tracking view.
- Booking payment data is resolved from the successful `Payment`; booking assignment data is resolved from the latest assignment.
- Report rows flatten cross-feature data for read-only consumption without modifying the normalized write model.
- Dashboard maps include every enum status, including zero counts.
