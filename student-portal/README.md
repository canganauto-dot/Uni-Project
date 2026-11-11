# Professor OS Student Portal — Phase 1

An isolated, visual-only student portal prototype. Every student, course, grade, and schedule shown here is fictional. Login and MFA are screen previews; they do not authenticate, create a session, call a service, or store credentials.

## Run locally

Requires Node.js 20.19+ (Node 24 is used for this prototype).

```powershell
cd student-portal
npm ci
npm run dev
```

Open the local URL printed by Vite. The dashboard opens by default. Use the sidebar, mobile navigation, or the login preview link to explore the screens.

## Verify

```powershell
npm run typecheck
npm run build
```

The production files are written to `dist/`. You can inspect them with `npm run preview`. No deployment is configured.

## Scope

React, TypeScript, Vite, Tailwind CSS and Lucide icons power this self-contained frontend. Fictional data lives in `src/demo.ts`; routes use URL hashes so every prototype screen can be opened directly without a router dependency. No desktop code, SQLite data, Supabase connection, API contract, or production authentication is included.

Next phase: agree on student-facing data access and security requirements, then replace the preview auth screens and demo module with real services.

## Design and motion

The [editable Figma redesign](https://www.figma.com/design/T99xrfBLmJDWl3L0WGYVMB) includes desktop and mobile concepts for every screen, shared color styles and components, and an interaction specification. `src/redesign.css` supplies the dark editorial palette and responsive layouts. Motion for React handles page and progress entrances; CSS handles shorter hover, sidebar, chart, and skeleton effects. Optional movement respects `prefers-reduced-motion`.

The dashboard uses the approved Studio Window direction in `src/Dashboard.tsx` and `src/dashboard.css`. The academic pages carry the same typography, dark surfaces, and fine dividers through scoped rules in `src/studio-pages.css`. Courses and results reuse the dashboard artwork through `src/CourseArtwork.tsx`. The four subject images come from one locally bundled contact sheet; its generation prompt is in `src/assets/studio-courses.prompt.txt`. Course entrances, score and attendance fills, chart reveals, and security controls use the existing Motion and CSS patterns.

Browser captures include [Studio Window on desktop](screenshots/studio-window-desktop.jpg), [Studio Window on mobile](screenshots/studio-window-mobile.jpg), [courses](screenshots/courses-desktop.jpg), and [mobile grades](screenshots/grades-mobile.jpg). The login and MFA screens remain visual previews only.
