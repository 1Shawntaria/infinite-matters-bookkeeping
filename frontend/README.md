# Infinite Matters Frontend

Next.js app for the Infinite Matters bookkeeping workspace.

## Getting Started

From the monorepo root:

```bash
cd frontend
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

By default, API calls target `http://localhost:8080`. Override that with `NEXT_PUBLIC_API_BASE_URL` when needed.

Example frontend environment variables live in [frontend/.env.example](/Users/shawntariaburden/Development/infinite-matters/frontend/.env.example).

## Checks

```bash
npm run lint
npm run build
```

## Backend

Run the Spring API from the monorepo root with:

```bash
cd backend
mvn spring-boot:run
```

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
