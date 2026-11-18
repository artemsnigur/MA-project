# CityFix — P0 Implementation Plan

As of 2026-11-18 (re-planned after the review: the frontend has not started yet). Submission deadline: **2026-11-22 at 23:59** (5% penalty per hour late). See `PROJECT_REQUIREMENTS.md` for the full scope.

Tasks are tracked as GitHub issues. Task IDs below (B0–B4, F0–F4) match the issue titles.

## 1. Scope

We build P0 only. Everything else is stretch work and goes into the report as future improvements.

**P0 (in scope):**

- Email/password sign-in, password reset by email, Citizen and Operator registration, pending Citizen activation by an Operator.
- Category management by Operators; Citizens see active categories only.
- Ticket creation with photos: local compression, thumbnail, stored as Base64 in Firestore by a WorkManager job (`NetworkType.CONNECTED`).
- My tickets list, ticket details, Operator panel with all tickets, lifecycle transitions with a timestamped history, ticket messaging.
- Anonymous mode: 10 km check, dashboard, public list, public details without author data or exact coordinates.
- Firestore security rules, offline support (Firestore built-in cache), EN + PT strings, portrait and landscape, MVVM.

**Out of scope unless P0 is done early (P1/P2):** MFA (an explicit requirement for both roles, so first in line for P1), video (needs Cloud Storage, i.e. the Blaze plan), reopen within 5 days and auto-close, 24 h reminder, notifications, duplicate aggregation within 10 m, 20 m operator geofence, Impact Detection, FCM.

## 2. Architecture decisions

- Backend is Firebase only, on the free Spark plan: Auth and Firestore, plus WorkManager on the device. No custom server.
- **Photos are stored in Firestore as Base64 JPEG, not in Cloud Storage.** Cloud Storage requires the paid Blaze plan on new projects. Each full photo is its own document (Firestore limit: 1 MiB per document); small thumbnails sit inside the ticket document so lists stay fast. This deviates from the assignment ("upload to Firebase Storage") and must be explained in the report.
- **Keep the code simple and explainable.** Write the simplest code that meets `PROJECT_REQUIREMENTS.md`: few files, plain structure, library helpers instead of own wrappers, no extra hardening or features the assignment does not ask for. Every line must be easy to explain at the defense.
- UI never calls Firebase directly: Compose screens → ViewModels → repository interfaces.
- Dependency wiring through a manual `AppContainer` (no Hilt). Fakes and Firebase implementations share the same interfaces; switching is one line.
- Maps use **osmdroid** (OpenStreetMap tiles, no API key) inside `AndroidView`. Google Maps SDK needs a billing account, like Cloud Storage. osmdroid is archived since 2024 but 6.1.20 works; fallback: MapLibre with OSM tiles.

### Code ownership

| Area | Owner |
| --- | --- |
| `data/` (models, repository interfaces, Firebase implementations), `worker/`, security rules, Firebase project | Backend (Artem) |
| `ui/` (screens, navigation, ViewModels, theme), `res/values*/strings.xml`, runtime permissions, photo picker and camera | Frontend (teammate) |
| Repository interfaces in `data/repository/` | Both; change only after agreement |

UI package layout: one package per feature under `ui/`, e.g. `ui/auth/`, `ui/tickets/`, `ui/operator/`, `ui/publicview/`, each with `XRoute.kt` (stateful, collects the ViewModel), `XScreen.kt` (stateless, with previews) and `XViewModel.kt`. Shared components go to `ui/components/`, navigation to `ui/navigation/`.

### Firestore schema

| Path | Holds | Readable by |
| --- | --- | --- |
| `users/{uid}` | role, status (PENDING / ACTIVE), name, email, phone, nif, location, photoBase64, impactDetectionEnabled | owner, operators |
| `categories/{id}` | name, active | everyone |
| `tickets/{id}` | authorId, categoryId, incidentType, lifecycleState, publicScope, location, description, media (refs with ~200 px Base64 thumbnails and upload state), timestamps | authors, operators |
| `tickets/{id}/media/{id}` | imageBase64 (JPEG, max 1280 px, ~400 KB as Base64), phase (REPORT / RESOLUTION), createdAt | authors, operators; everyone if the ticket is public |
| `tickets/{id}/history/{id}` | from, to, byUid, at, note | authors, operators |
| `tickets/{id}/messages/{id}` | senderUid, senderRole, text, sentAt | authors, operators |
| `publicTickets/{id}` | categoryId, incidentType, lifecycleState, thumbnailBase64, rounded location, createdAt, resolvedAt | everyone, including anonymous |

### Rules

- A ticket counts as submitted once its Firestore document is written. Photo processing (compress, encode, write to `media`) runs afterwards in WorkManager with `uploadState` PENDING / DONE / FAILED; the UI shows that state with a retry action.
- Ticket creation is a batch write (ticket + `publicTickets` + first history entry), never a transaction, because transactions fail offline.
- `publicTickets` is the only ticket data anonymous users read. It has no author data and coordinates rounded to about 1 km.
- Operator registration is allowed only for emails in the municipal domain, checked in the app. The domain is a constant.
- NIF uniqueness per role: after the Auth account is created, query `users` for the same role and NIF; if one exists, delete the new account and show an error.
- The 10 km center is a constant: Praça da República, Coimbra.
- Dashboard numbers come from `publicTickets`: total count, share of active tickets, average `resolvedAt - createdAt`. Anonymous users cannot read private tickets, so the dashboard counts public tickets only; state this in the report.
- In P0 only operators change the lifecycle state. Citizens talk through messages; citizen reopening is P1.
- State changes and other ticket edits use field updates (`update("lifecycleState", ...)`), never a full-document overwrite, so concurrent writers do not erase each other.
- Security rules stay simple (`firestore.rules`): anyone reads `categories`, `publicTickets` and the media of public tickets; everything else needs a signed-in user. Role checks live in the app.

## 3. Shared contract

The code is the source of truth; this section only says where it lives.

| File | Contents |
| --- | --- |
| `data/model/Enums.kt` | `UserRole`, `AccountStatus`, `IncidentType`, `LifecycleState` (with `isActive` and `allowedNext()`), `UploadState`, `MediaPhase` |
| `data/model/Models.kt` | `User`, `Category`, `Ticket`, `MediaRef`, `MediaContent`, `PublicTicket`, `StateChange`, `Message`, `DashboardStats`, `LatLng`; UI inputs `TicketDraft`, `RegistrationForm`; `SessionState` (Loading / Anonymous / SignedIn) |
| `data/repository/Repositories.kt` | `AuthRepository`, `UserAdminRepository`, `CategoryRepository`, `TicketRepository`, `MediaRepository`, `PublicRepository`, `MessageRepository`, `LocationProvider` |
| `data/fake/` | In-memory implementations with demo data (accounts: `ana@cm-coimbra.pt` operator, `rui@example.com` citizen, `marta@example.com` pending; password `password`) |
| `di/AppContainer.kt` | Which implementation each repository uses; `CreationExtras.appContainer()` for ViewModel factories |
| `data/AppConfig.kt` | Anonymous-area center and radius, municipal email domain, distance and rounding helpers |
| `data/media/Base64Images.kt` | `base64ToBitmap()` for showing stored photos |

Model rules: every field has a default value (Firestore `toObject()` needs a no-arg constructor), no `is` prefix on stored booleans, no `@Serializable`, enums stored as strings.

Allowed transitions (`LifecycleState.allowedNext()`):

- OPEN → INFORMATION_REQUEST or IN_RESOLUTION
- INFORMATION_REQUEST → OPEN or IN_RESOLUTION
- IN_RESOLUTION → RESOLVED (requires at least one RESOLUTION photo)
- RESOLVED → CLOSED

The repository enforces these; the UI only offers the valid ones.

## 4. Backend tasks (Artem)

**B0 · Setup, contract, fakes (Mon 16)**

- Create the Firebase project, register the Android app, add `google-services.json` and the Google Services plugin.
- Enable Email/Password sign-in and create Firestore (test mode) on the free Spark plan.
- Add dependencies: Firebase BoM (Auth, Firestore), WorkManager, Coroutines Play Services, Play Services Location, Coil, Navigation Compose, lifecycle ViewModel Compose.
- Fix `model/*.kt` to the shared contract and remove `@Serializable`, so the project builds.
- Push repository interfaces, in-memory fakes and `AppContainer`.

**B1 · Auth, users, categories (Wed 18)**

- `FirebaseAuthRepository`: sign in, register Citizen (PENDING) and Operator (ACTIVE), password reset, profile update, sign out; `session` emits Loading until Firebase restores the cached user.
- Registration: validate the form → create the Auth account → check that no user of the same role has this NIF (otherwise delete the account) → save `users/{uid}`.
- `Validation`: email, phone (9 digits), NIF (9 digits), municipal domain for operators.
- `data/media/Images.kt`: `uriToBase64Jpeg` (downscale, JPEG, Base64) for the profile photo, reused in B2.
- `UserAdminRepository`: pending citizens list, `observeUser`, activation.
- `CategoryRepository` (operators create the categories through the app).
- Simple `firestore.rules`, pasted into the console.

**B2 · Tickets and photos (Thu 19)**

- `createTicket`: copy the picked photos into `filesDir` right away (picker URIs lose permission later), then one batch write of `tickets`, `publicTickets` and the first history entry; photos start as PENDING.
- Image pipeline via `uriToBase64Jpeg`: max 1280 px, JPEG ~70%, ~200 px thumbnail; keep the full image under ~700 KB as Base64 (1 MiB document limit).
- `PhotoUploadWorker` (WorkManager, `NetworkType.CONNECTED`, exponential backoff): reads the copied files, writes `tickets/{id}/media/{mediaId}`, then updates the ticket's media list in a transaction (safe: the worker only runs online) and `publicTickets.thumbnailBase64`; deletes the local copies.
- `observeMyTickets` (`whereEqualTo("authorId")`, sorted on the device, so no index is needed), `observeAllTickets`, `observeTicket`, `retryUploads`, `MediaRepository.observeMedia`.

**B3 · Lifecycle, messages, public data (Fri 20, morning)**

- `changeState` (operators only) with allowed transitions, history entry, `resolvedAt`, mirroring to `publicTickets`; `addResolutionPhotos`.
- `MessageRepository` on `tickets/{id}/messages`.
- `PublicRepository` with dashboard stats; `FusedLocationProvider`-based `LocationProvider` with the 10 km check.
- Switch `CityFixApp` to the Firebase container; integration with the frontend.

**B4 · Offline test, demo data, report (Fri 20 – Sat 21)**

- Publish `firestore.rules` and check every flow with them.
- Offline test: create a ticket in airplane mode, reconnect, confirm sync and photo processing.
- Seed demo data: one Operator, one active Citizen, one pending Citizen, tickets in each state.
- Backend sections of the report: architecture, schema, rules, offline strategy, Base64 photos, dashboard scope, missing MFA.

## 5. Frontend tasks (teammate)

Build every screen against the fake repositories first; switching to real data is a one-line change in `AppContainer`.

**F0 · Navigation shell and strings (Wed 18, overdue)**

- Navigation Compose graph with three entry points (Anonymous, Citizen, Operator), chosen from `AuthRepository.session` (`Loading` → splash, `Anonymous`, `SignedIn` by role; PENDING citizen → pending screen).
- Shared `Scaffold` with a dynamic top bar (title and actions per screen) and a FAB where it fits.
- All strings in `values/strings.xml` (English) and `values-pt/strings.xml` (Portuguese) from the first screen.
- Screen pattern: a stateful `XRoute` that collects the ViewModel `StateFlow`, and a stateless `XScreen(state, onAction)` with a preview.

**F1 · Auth and profile screens (Thu 19, morning)**

- Sign in, forgot password, register Citizen (location capture, optional photo), register Operator.
- Inline validation errors; "account pending" screen for PENDING citizens.
- Profile screen: phone and photo editable; name, NIF and location read-only.

**F2 · Citizen ticket flow (Thu 19)**

- Create ticket form: active category dropdown, incident type, description, public/private, current location, photo picker and camera, at least one photo required.
- Runtime permissions: location and camera (notifications come with P1).
- My tickets list with state chip and thumbnail; ticket details with media, history and upload status with retry.
- Messages inside ticket details.

**F3 · Operator and anonymous screens (Fri 20)**

- Operator panel: all tickets, filters by state and category, exact coordinates, author contact.
- State change actions with a note dialog; resolution photo upload before RESOLVED.
- Categories screen (create, edit, activate, deactivate) and pending citizens screen (name, NIF, location, activate).
- Anonymous: location check screen (outside 10 km = blocked, with a sign-in option), dashboard, public list, public details with a zoomed-out osmdroid map (no Google Maps: it needs billing).

**F4 · Landscape, accessibility, report (Sat 21)**

- Landscape check on every screen: no lost input on rotation (state in ViewModels and `rememberSaveable`), scrollable forms.
- Accessibility: `contentDescription` on images and icons, 48 dp touch targets, readable contrast.
- Frontend sections of the report: navigation, MVVM, stateless/stateful split, localization, screenshots.

## 6. Git workflow

The teammate merges frontend work into `develop` without review. Artem reviews `develop` before it reaches `main`. Artem's backend work goes straight to `main`. Nobody pushes to `main` directly.

### Branches

| Branch | Purpose | How changes get in |
| --- | --- | --- |
| `main` | Reviewed, demo-ready code; this is what we submit | PR `develop` → `main` reviewed by Artem, or Artem's backend PRs |
| `develop` | Frontend integration branch; the repository's default branch | Teammate's feature PRs, merged by the teammate without review; syncs from `main` |
| `feature/F<n>-<short-name>` | One frontend task, e.g. `feature/F2-ticket-flow` | Teammate → `develop` |
| `feature/B<n>-<short-name>` | One backend task, e.g. `feature/B2-tickets-photos` | Artem → `main` |
| `fix/<short-name>` | A bug fix | Frontend → `develop`, backend → `main` |

Merge methods (they matter, the wrong one breaks the next sync):

| From → to | Method |
| --- | --- |
| `feature/F*` → `develop` | Squash and merge |
| `feature/B*` → `main` | Squash and merge |
| `develop` → `main` | **Create a merge commit** (never squash) |
| `main` → `develop` | `git merge` locally, then push |

### Frontend flow (teammate)

1. Start from the latest `develop`:

   ```bash
   git switch develop
   git pull
   git switch -c feature/F1-auth-screens
   ```

2. Work and commit in small steps. Commit messages are English, imperative, prefixed with the task ID:

   ```bash
   git add -A
   git commit -m "F1: Add sign-in screen with validation"
   ```

3. Before merging, check that `./gradlew assembleDebug` passes and the app runs. `develop` must always build.
4. Push and open a PR into `develop`, then merge it yourself with **Squash and merge**. No review is needed:

   ```bash
   git push -u origin feature/F1-auth-screens
   gh pr create --base develop --title "F1: Auth and profile screens" --body "Closes #8"
   gh pr merge --squash
   ```

   The branch is deleted and the issue closes automatically (`develop` is the default branch).
5. Every evening, or whenever a feature is ready to show, open a PR from `develop` into `main` (if one is not open already):

   ```bash
   gh pr create --base main --head develop --title "Frontend update: <date>"
   ```

   Artem is requested automatically and reviews it. Fixes go in as new `fix/` or `feature/` branches merged into `develop`; the open PR updates by itself. Artem merges it with **Create a merge commit**.

### Backend flow (Artem)

1. Branch from the latest `main`: `git switch main && git pull && git switch -c feature/B2-tickets-photos`.
2. Open a PR into `main`. A review from the teammate is welcome but not required; Artem merges it himself with **Squash and merge** (admin bypass of the approval rule).
3. Put `Closes #N` in the PR description: the issue closes when the squash commit reaches `develop` (the default branch) through the sync below.
4. Tell the teammate, so the backend change reaches `develop` (next section).

### Keeping branches in sync

Backend changes reach the frontend by merging `main` into `develop`. Whoever needs them does it (usually the teammate, after Artem says a backend PR is merged):

```bash
git switch develop
git pull
git merge origin/main
./gradlew assembleDebug
git push
```

To bring `develop` into your own feature branch: `git switch feature/F1-auth-screens && git merge develop`.

If there are conflicts, resolve them in Android Studio (Git → Resolve Conflicts), build, commit and push. In files you do not own, ask before resolving.

### Review of `develop` → `main` (Artem)

- It builds and runs; the screens follow the pattern (stateful Route + stateless Screen).
- No Firebase calls in `ui/`; no hardcoded user-facing strings; EN and PT strings present.
- No changes to `data/model/` or `data/repository/` interfaces without prior agreement.
- Problems → "Request changes" with comments; the teammate fixes them in `develop` through new branches.

### Rules

- Never push to `main`; never `git push --force` to `main` or `develop`.
- `develop` must always build: run `./gradlew assembleDebug` before every merge into it.
- Commit `google-services.json` (needed to build the submitted ZIP). Never commit `local.properties`, `.gradle/`, `build/`, `.idea/` or keystores.
- After the feature freeze (Sat 2026-11-21 evening) only `fix/` branches are merged, and the final `develop` → `main` PR is merged by Sat night.

### Repository settings (done)

- Default branch: `develop`. Squash merge and merge commits allowed, rebase merge disabled; head branches are deleted after merge.
- `main` is protected: PR with 1 approval required (admins may bypass), no force pushes, no deletion.
- `develop` is protected only against force pushes and deletion; no PR or review required.
- `.github/CODEOWNERS` requests Artem on PRs into `main`; `.github/pull_request_template.md` pre-fills the checklist.

## 7. Schedule

| Date | Backend (Artem) | Frontend (teammate) | Milestone |
| --- | --- | --- | --- |
| Mon 16 – Tue 17 | B0 done | — | Contract and fakes in `develop` |
| Wed 18 | B1 | F0 | Fallback decision at 22:00 |
| Thu 19 | B2 | F1, F2 | — |
| Fri 20 | B3, then B4 | F3 | Switch to Firebase, first integration |
| Sat 21 | B4, report | F4, report | Feature freeze in the evening |
| Sun 22 | Test, package | Test, screenshots | Done by 18:00, deadline 23:59 |

Each evening: open or update the `develop` → `main` PR, sync `main` into `develop`, run the app.

**Fallback:** if no F0 work is pushed by Wed 18 at 22:00, Artem builds the F0 shell on Thu morning (navigation by role, Scaffold, strings) and the frontend scope is cut in this order: profile photo, operator filters, dashboard average, map (show rounded coordinates as text instead).

## 8. Risks

| Risk | Fallback |
| --- | --- |
| Frontend is 1.5 days behind and silent | Fallback above; screens are built on fakes, so backend work never waits for them |
| Database open in test mode while the repo is public | Publish the simple `firestore.rules` |
| Integration on Fri 20 reveals contract mismatches | Contract changes only by agreement; fakes and Firebase implement the same interfaces; sync `main` into `develop` daily |
| Graders penalise missing MFA | Explain in the report; MFA is the first P1 item if time remains |
| Graders penalise Base64 photos instead of Cloud Storage | Explain the free-plan constraint in the report; if time allows, enable Blaze and move photos/videos to Storage (P1) |
| A photo exceeds the 1 MiB document limit | Compress harder (lower quality, then smaller size) until the Base64 string is under ~700 KB |
| Photo worker or offline sync misbehaves in the demo | Airplane-mode test in B4; seed demo data the night before |
| P0 runs late | Cut order: landscape polish, then dashboard average, then profile photo; never cut the ticket flow |

## 9. Submission checklist

- [ ] Clean build and full demo run on a real device: Citizen, Operator and Anonymous flows.
- [ ] Demo accounts and seed data: one Operator, one active Citizen, one pending Citizen, tickets in each state.
- [ ] Report PDF: technical decisions, functional choices, P1/P2 items as future improvements.
- [ ] Delete `.gradle` and `app/build`, zip as `AMOV2627.<nr1>.<nr2>.zip`, upload to Nónio.

## 10. Open questions

- Municipal email domain for operator registration.
- Exact coordinates of Praça da República, Coimbra.
- Student numbers for the ZIP file name.
