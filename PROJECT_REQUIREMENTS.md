# CityFix — Project Description and Requirements

This document summarizes the assignment *Mobile Architectures — Practical Work, Android-Kotlin, 2026/27*. It describes the required scope; it does not claim that these features are already implemented.

## 1. Goal

CityFix is an Android application written in Kotlin for civic engagement and municipal incident management. Residents report and track problems in public spaces, such as damaged roads, water leaks, waste collection issues, public lighting failures, and malfunctioning road signs.

The app must support incident creation and querying during intermittent connectivity, end-to-end traceability of each ticket, communication with municipal services, photo and video evidence of completed work, and proximity alerts between residents and municipal field teams. The assignment recommends Firebase or an equivalent cloud service architecture.

## 2. Roles and Access

### Anonymous Citizen

- No authentication is required. Access is restricted to users within 10 km of Praça da República.
- Show a dashboard with average repair time, total incident count, and the percentage of active incidents.
- Show active public incidents in the municipality, including category, status, and a thumbnail.
- Public incident details must hide the author's personal data and exact coordinates. Show available photos/videos and a map at a zoomed-out scale.

### Registered Citizen

- The user authenticates with the Citizen (Munícipe) profile. Access is available regardless of physical location.
- Registration collects name, email, phone number, Tax ID (NIF), geolocation, the Citizen profile, and an optional photo. Validate the data. NIF must be unique for this profile. The account remains pending until municipal services validate it by NIF.
- During registration, the user chooses whether to enable Impact Detection.
- Authentication uses email/password with multi-factor authentication (MFA); credential recovery is handled by email.
- Users can edit personal data except their name, NIF, and geolocation.
- Registered Citizens inherit all Anonymous Citizen features and can manage their own incidents.
- Incident creation requires a municipality-defined category, an incident type, a description, photos and/or videos, and a public/private scope. All form fields are mandatory, and at least one photo or video must be uploaded.
- Users can list all submitted tickets and their current states. Incident details include direct messaging with municipal services.
- A resolved ticket can be reopened within five days; reopening requires a justification.
- When enabled, Impact Detection recognizes abrupt vertical acceleration while commuting that may indicate a pothole or road hazard, then suggests creating a road defect report.
- Before accepting a ticket, check for another report with the same category and incident type within approximately 10 metres. Matching reports are aggregated into a group ticket that multiple citizens can follow.

### Municipal Operator

- The authenticated Operator profile is available regardless of physical location.
- Registration collects name, email, phone number, NIF, the Operator profile, and an optional photo. NIF must be unique for this profile. Validate the data, including that the email belongs to the municipal domain. In this prototype, operator accounts are activated immediately without a superior management role.
- Authentication uses email/password with MFA; credential recovery is handled by email.
- Operators can edit personal data except their name and NIF.
- Operators review pending Citizen registrations and can activate them. The review shows the applicant's name, NIF, and geolocation.
- Operators manage incident categories: create, edit, activate, and deactivate them. Citizens can use only active categories. Example categories include Public Roads, Water, Lighting, and Sanitation.
- The operational panel shows every submitted ticket, exact coordinates, author contact information, and the full media history.
- Operators control ticket lifecycle transitions with timestamps, request additional information, record the start of field work, and upload photos/videos of completed work.
- When a Municipal Operator comes within 20 metres of an active incident, notify its author(s) so they can monitor the intervention.

## 3. Tickets and Business Rules

Ticket lifecycle states:

1. **Open** — the Citizen has submitted the ticket and it has been registered.
2. **Information Request** — either the municipality or the Citizen can request additional information. The other party receives a notification and can reply.
3. **In Resolution** — the municipality starts field work or dispatches a team. A 20-metre geofence is activated and the ticket author(s) are notified.
4. **Resolved** — field technicians mark the issue resolved and attach mandatory photo/video evidence. The author receives a notification and has five days to reopen the ticket with a mandatory justification.
5. **Closed** — after five days without a contest, the ticket is archived and locked against further changes.

The incident form also has a classification called **Status** in the assignment: **Damage**, **Breakdown**, **Destruction**, or **Theft**. Keep these values separate from the ticket lifecycle states above.

## 4. Notifications

Support local and remote notifications, for example with Firebase Cloud Messaging:

- Ticket state transitions: Open, Information Request, In Resolution, Resolved, and Closed.
- Information requests and replies.
- Municipal vehicle/operator proximity alerts triggered by the 20-metre geofence.
- A reminder when 24 hours remain before a resolved ticket is permanently closed.

## 5. Data, Offline Support, and Media

- Use Firebase Authentication or an equivalent service for registration, sign-in, and credential management. Cache session tokens locally after the initial sign-in.
- Store and query tickets, categories, and messages with Cloud Firestore or an equivalent service. Use native offline caching for reads and writes and synchronize data across users.
- Compress images locally and generate optimized images and thumbnails before upload.
- Upload large photos and videos to Firebase Storage reliably and asynchronously through WorkManager, conditioned on an active network connection (`NetworkType.CONNECTED`).

## 6. Development and Quality Requirements

- Build the Android app in Kotlin with Jetpack Compose.
- Persist all data with services that enable seamless multi-user sharing; Firebase is advised.
- Use MVVM. Code organization, quality, and robustness are part of the assessment; Compose UI should use both stateless and stateful components appropriately.
- Request runtime permissions as needed, including foreground/background location, camera, notifications, and other permissions required by implemented features.
- English is the default app language. Support at least one additional language (Portuguese or another language), selected according to the device's system settings.
- Support portrait and landscape orientations without loss of functionality, visual degradation, or disrupted input.
- Provide intuitive, context-aware navigation with dynamic top bars, contextual actions, and floating action buttons.
- Implement accessibility mechanisms.

## 7. Submission

- The work may be individual or completed by a team of two. Team composition was to be confirmed by 2026-10-04.
- Deadline: **2026-11-22 at 23:59**. The assignment specifies a 5% penalty for each hour of delay.
- A functional defense is mandatory during lab sessions in the following week. Both students must attend a group defense. An individual oral defense is optional and scheduled later.
- Submit one ZIP file through Nónio containing the complete source code and assets required to build and run the project. Example two-student filename: `AMOV2627.<student_nr1>.<student_nr2>`. Non-compliant archive formats are subject to a penalty of up to 5% of the final grade.
- Before packaging, remove `<project>/.gradle` and `<project>/app/build`; the assignment specifies a penalty of up to 5% if these directories are included.
- Include a technical PDF report describing technical decisions, functional implementation choices, and possible future improvements.

## 8. Open Questions to Resolve Before Implementation

- The assignment calls both the incident classification (Damage, Breakdown, etc.) and lifecycle stages (Open, Resolved, etc.) “status”. This document treats them separately as an incident type/classification and a lifecycle state; confirm the intended model.
- The assignment does not identify which Praça da República defines the 10-km radius. Keep the center coordinates configurable rather than hard-coded.
- Impact Detection has no specified acceleration threshold, sampling frequency, or battery-use policy; these need a design decision.
- The visibility rules for private incidents are unspecified, including which users may list them and what anonymous users may see.
- Define how an aggregated ticket represents multiple authors, messages, lifecycle state, and evidence from multiple citizens.

