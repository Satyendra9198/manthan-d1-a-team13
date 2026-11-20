# Frontend — Task Manager

A small task manager built with **React 19 + TypeScript + Vite**, styled with **Tailwind v4 + shadcn/ui**, unit-tested with **Vitest + Testing Library**, and end-to-end tested with **Playwright**.

Features: add, complete, delete tasks; filter by All / Active / Completed; clear completed; persisted to `localStorage`.

## Scripts

| Command | What it does |
| --- | --- |
| `npm run dev` | Start the dev server |
| `npm run build` | Type-check and build to `dist/` |
| `npm run preview` | Serve the production build |
| `npm test` | Run Vitest unit/component tests once |
| `npm run test:watch` | Vitest in watch mode |
| `npm run test:e2e` | Build, serve, and run Playwright tests (Chromium) |
| `npm run test:all` | Unit + e2e |
| `npm run lint` | oxlint |

First-time e2e setup: `npx playwright install chromium`.

## Layout

```
src/
  lib/tasks.ts           pure task logic (unit-tested in tasks.test.ts)
  hooks/use-tasks.ts     state + localStorage persistence
  components/            TaskForm, TaskItem
  components/ui/         shadcn components (add more: npx shadcn@latest add <name>)
  App.tsx                page (component-tested in App.test.tsx)
e2e/                     Playwright specs
```
