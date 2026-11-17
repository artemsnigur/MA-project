# CityFix — P0 Implementation Plan

As of 2026-11-17. Submission deadline: **2026-11-22 at 23:59** (5% penalty per hour late). See `PROJECT_REQUIREMENTS.md` for the full scope.

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

**Out of scope unless P0 is done early (P1/P2):** video (needs Cloud Storage, i.e. the Blaze plan), MFA, reopen within 5 days and auto-close, 24 h reminder, notifications, duplicate aggregation within 10 m, 20 m operator geofence, Impact Detection, FCM.

## 2. Architecture decisions

- Backend is Firebase only, on the free Spark plan: Auth and Firestore, plus WorkManager on the device. No custom server.
- **Photos are stored in Firestore as Base64 JPEG, not in Cloud Storage.** Cloud Storage requires the paid Blaze plan on new projects. Each full photo is its own document (Firestore limit: 1 MiB per document); small thumbnails sit inside the ticket document so lists stay fast. This deviates from the assignment ("upload to Firebase Storage") and must be explained in the report.
- UI never calls Firebase directly: Compose screens → ViewModels → repository interfaces.
- Dependency wiring through a manual `AppContainer` (no Hilt). Fakes and Firebase implementations share the same interfaces; switching is one line.

### Code ownership

| Area | Owner |
| --- | --- |
| `data/` (models, repository interfaces, Firebase implementations), `worker/`, security rules, Firebase project | Backend (Artem) |
| `ui/` (screens, navigation, ViewModels, theme), `res/values*/strings.xml`, runtime permissions, photo picker and camera | Frontend (teammate) |
| Repository interfaces in `data/repository/` | Both; change only after agreement |

### Firestore schema

| Path | Holds | Readable by |
| --- | --- | --- |
| `users/{uid}` | role, status (PENDING / ACTIVE), name, email, phone, nif, location, photoBase64, impactDetectionEnabled | owner, operators |
| `nifs/{ROLE_nif}` | uid; makes NIF unique per role | nobody (create only if absent) |
| `categories/{id}` | name, active | everyone |
| `tickets/{id}` | authorIds, categoryId, incidentType, lifecycleState, publicScope, location, description, media (refs with ~200 px Base64 thumbnails and upload state), timestamps | authors, operators |
| `tickets/{id}/media/{id}` | imageBase64 (JPEG, max 1280 px, ~400 KB as Base64), phase (REPORT / RESOLUTION), createdAt | authors, operators; everyone if the ticket is public |
| `tickets/{id}/history/{id}` | from, to, byUid, at, note | authors, operators |
| `tickets/{id}/messages/{id}` | senderUid, senderRole, text, sentAt | authors, operators |
| `publicTickets/{id}` | categoryId, incidentType, lifecycleState, thumbnailBase64, rounded location, createdAt, resolvedAt | everyone, including anonymous |

### Rules

- A ticket counts as submitted once its Firestore document is written. Photo processing (compress, encode, write to `media`) runs afterwards in WorkManager with `uploadState` PENDING / DONE / FAILED; the UI shows that state with a retry action.
- Ticket creation is a batch write (ticket + `publicTickets` + first history entry), never a transaction, because transactions fail offline.
- `publicTickets` is the only ticket data anonymous users read. It has no author data and coordinates rounded to about 1 km.
- Operator registration is allowed only for emails in the municipal domain, checked in the app and in security rules. The domain is a constant.
- The 10 km center is a constant: Praça da República, Coimbra.
- Dashboard numbers come from `publicTickets`: total count, share of active tickets, average `resolvedAt - createdAt`.

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

**B1 · Auth, users, categories (Tue 17)**

- `FirebaseAuthRepository`: sign in, register Citizen (PENDING) and Operator (ACTIVE), password reset, profile update, sign out.
- NIF uniqueness through `nifs/{ROLE_nif}` in the same batch as `users/{uid}`.
- Validation helpers: email, Portuguese phone (9 digits), NIF (9 digits + check digit), municipal domain for operators.
- `UserAdminRepository`: pending citizens list, activation.
- `CategoryRepository` plus seed data: Public Roads, Water, Lighting, Sanitation.

**B2 · Tickets and photos (Tue 17 – Wed 18)**

- `createTicket`: batch write of `tickets`, `publicTickets` and the first history entry; media items start as PENDING.
- Image pipeline: downscale to max 1280 px, JPEG ~70%, ~200 px thumbnail, both Base64-encoded; check the full image stays under ~700 KB as Base64.
- `PhotoUploadWorker` (WorkManager, `NetworkType.CONNECTED`, exponential backoff): compresses the picked photos, writes `tickets/{id}/media/{mediaId}`, sets the thumbnail and DONE/FAILED in the ticket, updates `publicTickets.thumbnailBase64`.
- `observeMyTickets`, `observeAllTickets`, `observeTicket`, `retryUploads`, `MediaRepository.observeMedia`.

**B3 · Lifecycle, messages, public data (Thu 19)**

- `changeState` with allowed transitions, history entry, `resolvedAt`, mirroring to `publicTickets`; `addResolutionMedia`.
- `MessageRepository` on `tickets/{id}/messages`.
- `PublicRepository` with dashboard stats; `LocationProvider` with the 10 km check.

**B4 · Security rules and offline (Fri 20)**

- Firestore rules: role and status checks via `get(users/uid)`, owner-only fields, public read only for `categories` and `publicTickets`, no role escalation.
- Rules for `tickets/{id}/media`: authors and operators write; read also allowed for public tickets.
- Offline test: create a ticket in airplane mode, reconnect, confirm sync and upload.
- Backend sections of the report: architecture, schema, rules, offline and upload strategy.

## 5. Frontend tasks (teammate)

Build every screen against the fake repositories first; switching to real data is a one-line change in `AppContainer`.

**F0 · Navigation shell and strings (Mon 16 – Tue 17)**

- Navigation Compose graph with three entry points (Anonymous, Citizen, Operator), chosen from `currentUser`.
- Shared `Scaffold` with a dynamic top bar (title and actions per screen) and a FAB where it fits.
- All strings in `values/strings.xml` (English) and `values-pt/strings.xml` (Portuguese) from the first screen.
- Screen pattern: a stateful `XRoute` that collects the ViewModel `StateFlow`, and a stateless `XScreen(state, onAction)` with a preview.

**F1 · Auth and profile screens (Tue 17)**

- Sign in, forgot password, register Citizen (location capture, optional photo), register Operator.
- Inline validation errors; "account pending" screen for PENDING citizens.
- Profile screen: phone and photo editable; name, NIF and location read-only.

**F2 · Citizen ticket flow (Wed 18)**

- Create ticket form: active category dropdown, incident type, description, public/private, current location, photo picker and camera, at least one photo required.
- Runtime permissions: location, camera, notifications (Android 13+).
- My tickets list with state chip and thumbnail; ticket details with media, history and upload status with retry.
- Messages inside ticket details.

**F3 · Operator and anonymous screens (Thu 19)**

- Operator panel: all tickets, filters by state and category, exact coordinates, author contact.
- State change actions with a note dialog; resolution photo upload before RESOLVED.
- Categories screen (create, edit, activate, deactivate) and pending citizens screen (name, NIF, location, activate).
- Anonymous: location check screen (outside 10 km = blocked, with a sign-in option), dashboard, public list, public details with a zoomed-out map.

**F4 · Landscape, accessibility, report (Fri 20 – Sat 21)**

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
3. Close the issue by hand (`gh issue close <n>`): closing keywords only work for PRs into the default branch (`develop`).
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
- Commit `google-services.json` (needed to build the submitted ZIP). Never commit `local.properties`, `.gradle/`, `build/` or keystores.
- After the feature freeze (Sat 2026-11-21 evening) only `fix/` branches are merged, and the final `develop` → `main` PR is merged by Sat night.

### Repository settings (done)

- Default branch: `develop`. Squash merge and merge commits allowed, rebase merge disabled; head branches are deleted after merge.
- `main` is protected: PR with 1 approval required (admins may bypass), no force pushes, no deletion.
- `develop` is protected only against force pushes and deletion; no PR or review required.
- `.github/CODEOWNERS` requests Artem on PRs into `main`; `.github/pull_request_template.md` pre-fills the checklist.

## 7. Schedule

| Date | Milestone |
| --- | --- |
| Mon 2026-11-16 | Contract, fakes and build fix pushed (B0); frontend starts F0 |
| Thu 2026-11-19 | First real integration: frontend switches from fakes to Firebase |
| Sat 2026-11-21 (evening) | Feature freeze; bug fixes only afterwards |
| Sun 2026-11-22, 18:00 | Testing, report and packaging done |
| Sun 2026-11-22, 23:59 | Submission deadline |

Each evening: open or update the `develop` → `main` PR, sync `main` into `develop`, run the app.

## 8. Risks

| Risk | Fallback |
| --- | --- |
| Integration on 2026-11-19 reveals contract mismatches | Contract changes only by agreement; fakes and Firebase implement the same interfaces; sync `main` into `develop` daily |
| Security rules block legitimate flows late | Write rules on Fri 20 against finished flows; test with the emulator or the Rules Playground |
| Graders penalise Base64 photos instead of Cloud Storage | Explain the free-plan constraint in the report; if time allows, enable Blaze and move photos/videos to Storage (P1) |
| A photo exceeds the 1 MiB document limit | Compress harder (lower quality, then smaller size) until the Base64 string is under ~700 KB |
| Upload worker or offline sync misbehaves in the demo | Airplane-mode test on Fri 20; seed demo data the night before |
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
