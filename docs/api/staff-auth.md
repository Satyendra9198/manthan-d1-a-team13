# Staff sign-in

Figma: **Staff – Login** ("Staff sign in", work email and password).

## `POST /api/v1/auth/login` (public)

```json
{ "email": "riya.warehouse@returnright.example", "password": "Password@123" }
```

**200**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-20T20:30:00Z",
  "staff": { "id": "aaaaaaaa-0000-0000-0000-000000000001",
             "email": "riya.warehouse@returnright.example",
             "fullName": "Riya Kapoor", "role": "WAREHOUSE_AGENT" }
}
```

Send the token on every staff request: `Authorization: Bearer <accessToken>`. Tokens last 8 hours; after that, sign in again.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Email or password missing or malformed. |
| 401 | `INVALID_CREDENTIALS` | Unknown email, wrong password or deactivated account. All three give the same answer. |

## `GET /api/v1/auth/me` (any staff token)

Returns the `staff` object above. 401 `UNAUTHORIZED` without a valid token.

## Who can call what

| Area | Path | Role |
|---|---|---|
| Customer | `/api/v1/customer/**` | public |
| Warehouse | `/api/v1/warehouse/**` | `WAREHOUSE_AGENT` |
| Finance | `/api/v1/finance/**` | `FINANCE_REVIEWER` |
| Operations | `/api/v1/operations/**` | `OPERATIONS_MANAGER` |

No token → 401 `UNAUTHORIZED`; wrong role → 403 `FORBIDDEN`.

## Demo accounts (`dev` profile)

Every password is `Password@123`.

| Email | Role |
|---|---|
| `riya.warehouse@returnright.example` | Warehouse agent |
| `arun.warehouse@returnright.example` | Warehouse agent |
| `kavya.finance@returnright.example` | Finance reviewer |
| `manoj.ops@returnright.example` | Operations manager |

## Configuration

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signing key, at least 32 characters. **Required** outside the `dev` profile; the app won't start without it. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins, default `http://localhost:5173` (Vite). |
