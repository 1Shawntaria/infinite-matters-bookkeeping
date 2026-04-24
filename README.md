# Infinite Matters

Monorepo for the Infinite Matters bookkeeping application.

## Structure

- `backend/` - Spring Boot API, Flyway migrations, and backend tests.
- `frontend/` - Next.js app for the bookkeeping workspace.

## Common Commands

Run backend tests:

```bash
cd backend
mvn test
```

Run the backend locally:

```bash
cd backend
mvn spring-boot:run
```

Run frontend lint:

```bash
cd frontend
npm run lint
```

Build the frontend:

```bash
cd frontend
npm run build
```

Run the frontend locally:

```bash
cd frontend
npm run dev
```

## IDE Run Configurations

Shared JetBrains run configurations live in `.run/`:

- `Backend Spring Boot` starts the Spring Boot API from `backend/`.
- `Frontend Dev` starts the Next.js app from `frontend/`.
- `Frontend Dev (Backend Ready)` waits for `http://localhost:8080/actuator/health`, then starts the frontend.
- `Full Stack Dev (Healthy)` starts the backend and the backend-ready frontend together.

## Continuous Integration

GitHub Actions runs:

- backend tests
- frontend lint and production build
- frontend Playwright smoke tests

on pull requests and pushes to `master` when backend, frontend, or workflow files change.

## Production Configuration

When the backend runs with the `prod` profile, startup validation fails fast unless security-sensitive settings are hardened:

- `BOOKKEEPING_AUTH_TOKEN_SECRET` must be unique and at least 32 characters.
- `DATABASE_URL` must point to PostgreSQL or another non-H2 production database.
- `BOOKKEEPING_AUTH_COOKIES_SECURE` must stay `true`.
- `BOOKKEEPING_AUTH_RESPONSE_TOKENS_ENABLED` must stay `false`.
- `BOOKKEEPING_SECURITY_ALLOWED_ORIGINS` must list HTTPS frontend origins only.
- `BOOKKEEPING_AUTH_PASSWORD_RESET_BASE_URL` must be an HTTPS URL.
- `BOOKKEEPING_NOTIFICATIONS_PROVIDER_WEBHOOK_SECRET` is required.
- When `BOOKKEEPING_NOTIFICATIONS_EMAIL_PROVIDER=sendgrid`, the SendGrid API key, from-email, and webhook public key are required.

## Environment Setup

Example environment files are checked in at:

- [backend/.env.example](/Users/shawntariaburden/Development/infinite-matters/backend/.env.example)
- [frontend/.env.example](/Users/shawntariaburden/Development/infinite-matters/frontend/.env.example)

Typical local setup:

```bash
cp backend/.env.example backend/.env.local
cp frontend/.env.example frontend/.env.local
```

Then adjust values as needed for your machine or deployment target.
