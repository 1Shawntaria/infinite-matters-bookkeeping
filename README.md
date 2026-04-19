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

## Continuous Integration

GitHub Actions runs the backend test suite and frontend lint/build checks on pull requests and pushes to `master` when backend, frontend, or workflow files change.
