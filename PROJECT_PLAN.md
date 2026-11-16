# CityFix — P0 Implementation Plan

As of 2026-11-16. Submission deadline: **2026-11-22 at 23:59** (5% penalty per hour late). See `PROJECT_REQUIREMENTS.md` for the full scope.

Tasks are tracked as GitHub issues. Task IDs below (B0–B4, F0–F4) match the issue titles.

## 1. Scope

We build P0 only. Everything else is stretch work and goes into the report as future improvements.

**P0 (in scope):**

- Email/password sign-in, password reset by email, Citizen and Operator registration, pending Citizen activation by an Operator.
- Category management by Operators; Citizens see active categories only.
- Ticket creation with photos: local compression, thumbnail, upload via WorkManager (`NetworkType.CONNECTED`).
- My tickets list, ticket details, Operator panel with all tickets, lifecycle transitions with a timestamped history, ticket messaging.
- Anonymous mode: 10 km check, dashboard, public list, public details without author data or exact coordinates.
- Firestore and Storage security rules, offline support (Firestore built-in cache), EN + PT strings, portrait and landscape, MVVM.

**Out of scope unless P0 is done early (P1/P2):** MFA, reopen within 5 days and auto-close, 24 h reminder, notifications, duplicate aggregation within 10 m, video, 20 m operator geofence, Impact Detection, FCM.

## 2. Architecture decisions

- Backend is Firebase only: Auth, Firestore, Storage, plus WorkManager on the device. No custom server.
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
| `users/{uid}` | role, status (PENDING / ACTIVE), name, email, phone, nif, location, photoUrl, impactDetectionEnabled | owner, operators |
| `nifs/{ROLE_nif}` | uid; makes NIF unique per role | nobody (create only if absent) |
| `categories/{id}` | name, active | everyone |
| `tickets/{id}` | authorIds, categoryId, incidentType, lifecycleState, publicScope, lat/lng, description, media, timestamps | authors, operators |
| `tickets/{id}/history/{id}` | from, to, byUid, at, note | authors, operators |
| `tickets/{id}/messages/{id}` | senderUid, senderRole, text, sentAt | authors, operators |
| `publicTickets/{id}` | categoryName, incidentType, lifecycleState, thumbnailUrl, rounded location, createdAt, resolvedAt | everyone, including anonymous |

### Rules

- A ticket counts as submitted once its Firestore document is written. Media upload runs afterwards with `uploadState` PENDING / DONE / FAILED; the UI shows that state with a retry action.
- Ticket creation is a batch write (ticket + `publicTickets` + first history entry), never a transaction, because transactions fail offline.
- `publicTickets` is the only ticket data anonymous users read. It has no author data and coordinates rounded to about 1 km.
- Operator registration is allowed only for emails in the municipal domain, checked in the app and in security rules. The domain is a constant.
- The 10 km center is a constant: Praça da República, Coimbra.
- Dashboard numbers come from `publicTickets`: total count, share of active tickets, average `resolvedAt - createdAt`.

## 3. Shared contract

Model rules: every field has a default value (Firestore `toObject()` needs a no-arg constructor), no `is` prefix on booleans, no `@Serializable`, enums stored as strings.

```kotlin
enum class UserRole { CITIZEN, OPERATOR }
enum class AccountStatus { PENDING, ACTIVE }
enum class IncidentType { DAMAGE, BREAKDOWN, DESTRUCTION, THEFT }
enum class LifecycleState { OPEN, INFORMATION_REQUEST, IN_RESOLUTION, RESOLVED, CLOSED }
enum class UploadState { PENDING, DONE, FAILED }
enum class MediaPhase { REPORT, RESOLUTION }

data class User(val id: String = "", val role: UserRole = UserRole.CITIZEN, val status: AccountStatus = AccountStatus.PENDING,
    val name: String = "", val email: String = "", val phone: String = "", val nif: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val photoUrl: String? = null,
    val impactDetectionEnabled: Boolean = false)

data class Category(val id: String = "", val name: String = "", val active: Boolean = true)

data class MediaItem(val id: String = "", val url: String? = null, val thumbnailUrl: String? = null,
    val video: Boolean = false, val phase: MediaPhase = MediaPhase.REPORT, val uploadState: UploadState = UploadState.PENDING)

data class Ticket(val id: String = "", val authorIds: List<String> = emptyList(), val categoryId: String = "",
    val incidentType: IncidentType = IncidentType.DAMAGE, val lifecycleState: LifecycleState = LifecycleState.OPEN,
    val publicScope: Boolean = true, val description: String = "", val latitude: Double = 0.0, val longitude: Double = 0.0,
    val media: List<MediaItem> = emptyList(), val createdAt: Long = 0, val updatedAt: Long = 0, val resolvedAt: Long? = null)

data class PublicTicket(val id: String = "", val categoryName: String = "", val incidentType: IncidentType = IncidentType.DAMAGE,
    val lifecycleState: LifecycleState = LifecycleState.OPEN, val thumbnailUrl: String? = null,
    val approxLatitude: Double = 0.0, val approxLongitude: Double = 0.0, val createdAt: Long = 0, val resolvedAt: Long? = null)

data class StateChange(val from: LifecycleState? = null, val to: LifecycleState = LifecycleState.OPEN,
    val byUid: String = "", val at: Long = 0, val note: String? = null)

data class Message(val id: String = "", val senderUid: String = "", val senderRole: UserRole = UserRole.CITIZEN,
    val text: String = "", val sentAt: Long = 0)

data class TicketDraft(val categoryId: String, val incidentType: IncidentType, val description: String,
    val publicScope: Boolean, val latitude: Double, val longitude: Double, val mediaUris: List<Uri>)
```

```kotlin
interface AuthRepository {
    val currentUser: Flow<User?>                                      // null = anonymous
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun registerCitizen(form: RegistrationForm): Result<Unit>  // status PENDING
    suspend fun registerOperator(form: RegistrationForm): Result<Unit> // status ACTIVE, municipal domain only
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun updateProfile(phone: String, photoUri: Uri?): Result<Unit>
    suspend fun signOut()
}

interface UserAdminRepository {
    fun observePendingCitizens(): Flow<List<User>>
    suspend fun activateCitizen(uid: String): Result<Unit>
}

interface CategoryRepository {
    fun observeCategories(activeOnly: Boolean): Flow<List<Category>>
    suspend fun saveCategory(category: Category): Result<Unit>         // create or edit
    suspend fun setActive(id: String, active: Boolean): Result<Unit>
}

interface TicketRepository {
    fun observeMyTickets(): Flow<List<Ticket>>
    fun observeAllTickets(): Flow<List<Ticket>>                        // operator
    fun observeTicket(id: String): Flow<Ticket?>
    fun observeHistory(id: String): Flow<List<StateChange>>
    suspend fun createTicket(draft: TicketDraft): Result<String>
    suspend fun changeState(id: String, to: LifecycleState, note: String?): Result<Unit>
    suspend fun addResolutionMedia(id: String, uris: List<Uri>): Result<Unit>
    suspend fun retryUploads(id: String)
}

interface PublicRepository {
    fun observePublicTickets(): Flow<List<PublicTicket>>
    fun observePublicTicket(id: String): Flow<PublicTicket?>
    fun observeDashboard(): Flow<DashboardStats>                       // total, activePercent, avgRepairHours
}

interface MessageRepository {
    fun observeMessages(ticketId: String): Flow<List<Message>>
    suspend fun sendMessage(ticketId: String, text: String): Result<Unit>
}

interface LocationProvider {
    suspend fun currentLocation(): LatLng?
    fun isWithinAnonymousArea(location: LatLng): Boolean               // 10 km from Praça da República
}
```

Allowed transitions for `changeState`:

- OPEN → INFORMATION_REQUEST or IN_RESOLUTION
- INFORMATION_REQUEST → OPEN or IN_RESOLUTION
- IN_RESOLUTION → RESOLVED (requires at least one RESOLUTION media item)
- RESOLVED → CLOSED

The repository enforces these; the UI only offers the valid ones.

## 4. Backend tasks (Artem)

**B0 · Setup, contract, fakes (Mon 16)**

- Create the Firebase project, register the Android app, add `google-services.json` and the Google Services plugin.
- Switch to the Blaze plan with a 1 EUR budget alert (Cloud Storage requires it on new projects).
- Add dependencies: Firebase BoM (Auth, Firestore, Storage), WorkManager, Coroutines Play Services, Play Services Location, Coil.
- Fix `model/*.kt` to the shared contract and remove `@Serializable`, so the project builds.
- Push repository interfaces, in-memory fakes and `AppContainer`.

**B1 · Auth, users, categories (Tue 17)**

- `FirebaseAuthRepository`: sign in, register Citizen (PENDING) and Operator (ACTIVE), password reset, profile update, sign out.
- NIF uniqueness through `nifs/{ROLE_nif}` in the same batch as `users/{uid}`.
- Validation helpers: email, Portuguese phone (9 digits), NIF (9 digits + check digit), municipal domain for operators.
- `UserAdminRepository`: pending citizens list, activation.
- `CategoryRepository` plus seed data: Public Roads, Water, Lighting, Sanitation.

**B2 · Tickets and media upload (Tue 17 – Wed 18)**

- `createTicket`: batch write of `tickets`, `publicTickets` and the first history entry; media items start as PENDING.
- Image pipeline: downscale to max 1920 px, JPEG ~80%, 320 px thumbnail, saved to app storage before upload.
- `MediaUploadWorker` (WorkManager, `NetworkType.CONNECTED`, exponential backoff): uploads to Storage, writes URLs and DONE/FAILED, updates `publicTickets.thumbnailUrl`.
- `observeMyTickets`, `observeAllTickets`, `observeTicket`, `retryUploads`.

**B3 · Lifecycle, messages, public data (Thu 19)**

- `changeState` with allowed transitions, history entry, `resolvedAt`, mirroring to `publicTickets`; `addResolutionMedia`.
- `MessageRepository` on `tickets/{id}/messages`.
- `PublicRepository` with dashboard stats; `LocationProvider` with the 10 km check.

**B4 · Security rules and offline (Fri 20)**

- Firestore rules: role and status checks via `get(users/uid)`, owner-only fields, public read only for `categories` and `publicTickets`, no role escalation.
- Storage rules: signed-in writes to own ticket paths, size and type limits.
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

One task = one issue = one branch = one pull request into `main`. Every PR gets a review from the other person before it is merged. Nobody pushes to `main` directly.

### Branches

| Branch | Purpose | Who merges into it |
| --- | --- | --- |
| `main` | Always builds and runs; this is what we demo and submit | Only through reviewed PRs |
| `feature/<ID>-<short-name>` | One task, e.g. `feature/F2-ticket-flow`, `feature/B2-media-upload` | Its author |
| `fix/<short-name>` | A bug fix, e.g. `fix/rotation-loses-form` | Its author |

A large task may be split into several branches and PRs (`feature/F3-operator-panel`, `feature/F3-categories`), each closing part of the issue.

### Task flow

1. Pick an issue assigned to you and move it to "In progress" (or comment that you started).
2. Start from the latest `main`:

   ```bash
   git switch main
   git pull
   git switch -c feature/F1-auth-screens
   ```

3. Work and commit in small steps. Commit messages are English, imperative, prefixed with the task ID:

   ```bash
   git add -A
   git commit -m "F1: Add sign-in screen with validation"
   ```

4. Push the branch and open a PR into `main` the same day, even if the task is not finished (mark it as draft):

   ```bash
   git push -u origin feature/F1-auth-screens
   gh pr create --base main --draft --title "F1: Auth and profile screens" --body "Closes #<issue number>"
   ```

5. When the task is done, check the PR checklist below, then mark the PR "Ready for review". The reviewer is requested automatically through `.github/CODEOWNERS` (or add them with `gh pr edit --add-reviewer <login>`).
6. The reviewer reviews the same day: approve, or "Request changes" with comments.
7. The author fixes review comments with new commits in the same branch and pushes; the PR updates by itself. Do not open a new PR.
8. After approval the author merges with **Squash and merge**. The branch is deleted automatically and the issue closes through `Closes #N`.
9. Switch back to `main`, `git pull`, start the next task.

### Reviews

- Reviewer for frontend PRs: Artem (`artemsnigur`). Reviewer for backend PRs: the teammate (`Musababa123-code`).
- Review target: within a few hours during the day. If a backend PR waits more than 2 hours and blocks work, Artem may merge it after a successful local build and ask for a review afterwards.
- The reviewer checks: it builds, it follows the screen pattern (stateful Route + stateless Screen), no Firebase calls in `ui/`, no hardcoded user-facing strings, no unrelated changes.
- Any change to `data/model/` or `data/repository/` interfaces needs explicit approval from both of us, because it breaks the other side.

### PR checklist

- [ ] `./gradlew assembleDebug` passes locally.
- [ ] The app runs and the changed screens work in portrait and landscape.
- [ ] All new strings exist in `values/strings.xml` and `values-pt/strings.xml`.
- [ ] UI PRs include a screenshot in the description.
- [ ] The PR body links the issue (`Closes #N`).

### Keeping the branch up to date

If `main` moved while you work (for example, the other person merged), bring the changes into your branch with a merge (not a rebase):

```bash
git switch main
git pull
git switch feature/F1-auth-screens
git merge main
```

If there are conflicts, resolve them in Android Studio (Git → Resolve Conflicts), build, commit and push. When unsure, stop and ask before resolving conflicts in files you do not own.

### Rules

- Never push to `main`, never `git push --force` on a branch someone else uses.
- Keep PRs small: ideally under one day of work and a few hundred lines.
- Commit `google-services.json` (needed to build the submitted ZIP). Never commit `local.properties`, `.gradle/`, `build/` or keystores.
- After the feature freeze (Sat 2026-11-21 evening) only `fix/` branches are merged.

### Repository setup (Artem, once)

- Settings → General: allow only squash merging, enable "Automatically delete head branches".
- Settings → Branches: protect `main` (require a pull request with 1 approval, block force pushes).
- `.github/CODEOWNERS`: Artem owns everything by default (so frontend PRs request him automatically); `data/`, `model/`, `worker/` and the security rules are owned by the teammate (so backend PRs request him).
- `.github/pull_request_template.md` pre-fills every PR with the checklist above.

## 7. Schedule

| Date | Milestone |
| --- | --- |
| Mon 2026-11-16 | Contract, fakes and build fix pushed (B0); frontend starts F0 |
| Thu 2026-11-19 | First real integration: frontend switches from fakes to Firebase |
| Sat 2026-11-21 (evening) | Feature freeze; bug fixes only afterwards |
| Sun 2026-11-22, 18:00 | Testing, report and packaging done |
| Sun 2026-11-22, 23:59 | Submission deadline |

Each evening: merge to `main`, run the app, close finished issues.

## 8. Risks

| Risk | Fallback |
| --- | --- |
| Integration on 2026-11-19 reveals contract mismatches | Contract changes only by agreement; fakes and Firebase implement the same interfaces |
| Security rules block legitimate flows late | Write rules on Fri 20 against finished flows; test with the emulator or the Rules Playground |
| Blaze billing setup is delayed | Start it on day 0; meanwhile keep media local and the worker ready |
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
