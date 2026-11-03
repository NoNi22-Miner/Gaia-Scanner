# Mobile PDF/Image Converter (name TBD)

## What it is
Free mobile app, Android first. Converts PDF to JPG/PNG and JPG/PNG to PDF.
Convert only: no editing of content, no OCR, no Word conversion.
All conversion runs on the phone. Structure reference: Clear Scan by Indy Mobile (import many files, PDF import with DPI setting, PDF export without watermark, folders, trash, sorting, Drive sync). Skip its in-app camera scanning, filters, markup, ads and subscriptions.

Pro tier (paid):
1. Auto-upload every file to a Google Drive folder the user designates.
2. Auto-create folders there (the app needs the user's authorization).
3. One Pro account on up to 20 devices (company and enterprise use).
4. Data control: members can only add files (stored PDFs). Delete and edit are blocked except for the owner device.

## Decisions made
- Conversion on device. No server-side conversion.
- Google Drive scope: drive.file only (non-sensitive). Never request drive or drive.readonly.
- Drive roles have no add-only role, so Drive sharing cannot enforce Pro rule 4. Rules live in a small backend.
- The owner's Drive folder is shared with nobody. Member devices only submit.
- Free tier needs no login in v1.
- Stack: native Android, Kotlin + Jetpack Compose. PDF to images uses Android's built-in PdfRenderer.
- Images to PDF uses our own JpegPdfWriter (app/.../core/JpegPdfWriter.kt): JPEG data goes into the PDF unchanged, one page written at a time. Chosen in phase 2 because Android's PdfDocument stored photos uncompressed (see the test results below). No third-party PDF library.
- Platforms: Android only in v1. Decide on iOS after v1 ships (the Google Picker docs do not mention iOS).
- PC version: deferred until after mobile, not dropped. Not in the current phases. Earlier plan was Tauri or Electron with pdf.js and pdf-lib.
- minSdk 29 (Android 10). Older, low-spec phones are out.
- Inputs: PDF, JPG, PNG, plus WebP and HEIC/HEIF where the phone decodes them. Outputs: JPG, PNG, PDF only.
- Rotating a page 90° before making a PDF is allowed (layout, not content editing).
- Camera: a "Take photo" button opens the phone's own camera app. No in-app camera or scanner.
- Owner: one bound owner device per org. If it is lost, the owner proves identity with a TOTP code from an authenticator app (e.g. Google Authenticator) set up at enrollment, then binds a new device. Design the details in phase 5.
- Build (phase 2): AGP 9.5 with built-in Kotlin, Kotlin 2.4.21, KSP 2.3.13, Gradle 9.8.2, compileSdk 37, targetSdk 36. Package name com.gaiascanner.app is a placeholder; make it final before the first Play upload.
- The cloud sandbox cannot reach dl.google.com or downloads.gradle.org, so the Android build runs only in GitHub Actions (.github/workflows/android.yml). scripts/core-tests-local.sh runs the plain-Kotlin tests locally.
- Long jobs use WorkManager foreground work with type dataSync (the docs list "local file processing" under it).

## Open decisions: ask the user before coding
- Full list per phase: docs/SPEC.md section 8. Spec: docs/SPEC.md.
- Owner recovery details (phase 5): the authenticator must not live only on the owner phone, or a lost phone loses both. Backup codes? What happens to the old device after re-binding? Does the owner also sign in with Google (needed for Drive anyway)?
- Member uploads: through the backend, or direct to Drive via resumable sessions created by the backend (not verified).
- Is Play billing required for invoiced company plans? Not confirmed.

## Unverified: prototype and test before building on them
- Does drive.file cover folders the app creates?
- Can the app create files inside a folder the user picked with the Google Picker (PICKER_ALLOW_FOLDER_SELECTION)?
- How one user's app instance sees files created by another user.
- Under drive.file, can the Android app see files the backend created, if both use OAuth clients in the same Google Cloud project?
- Licenses of any PDF library chosen. Read its LICENSE file.
- The docs advise rendering untrusted PDFs in an isolated process. Not built yet.
- Real phones: camera app photos at full size, 50 real photos to PDF, output opening in Chrome, Drive and Acrobat.

## Tested on CI emulators, API 29 and 36 (phase 2, synthetic files)
- PdfRenderer leaves blank areas transparent, so pages must be painted white first (done).
- RENDER_MODE_FOR_PRINT and FOR_DISPLAY gave identical pixels on a simple page.
- Password PDF without password: SecurityException, shown as PASSWORD. Unlocking with LoadParams and write() works on API 36.
- 100-page A4 PDF: JPG at 150 DPI in about 1 s, PNG at 300 DPI in about 21 s. A 2000 x 2000 pt page at 600 DPI is capped to 4000 x 4000 px.
- HEIC decodes on both. A 64 x 48 HEIC came back as 64 x 64 on API 29 (correct on 36).
- PdfDocument stores images without JPEG compression: 10 photos (36 MB of JPEG) made a 192 MB PDF at 300 DPI and a 91 MB PDF at 200 DPI. Native memory grew about 12 MB per page until the file was written (bitmaps are not freed by hand, so part of that may be ours).

## Data (metadata only; files stay on the phone and in Drive)
- Settings: output format, JPEG quality, render DPI, page size and margins, output folder, theme, language, Drive sync on/off.
- Job: id, type, inputs, options, status, progress, error, times.
- Document: id, name, kind, page count, size, local path, folder id, Drive file id, created, modified, trashed at.
- Folder: id, name, parent id. Page: id, document id, order, image path, width, height, rotation (0, 90, 180, 270).
- SyncRecord: document id, Drive file id, last synced, status.
- Pro: Organization (plan, expiry, device limit 20, owner, Drive folder), Device (org, name, role, enrolled, last seen, status), Upload (org, device, name, size, hash, Drive file id, status), AuditLog, Invite, Subscription.

## Phases (one session per phase; clear the session between phases)
1. Spec and stack decision. No code.
2. Convert engine and local save (free v1).
3. Library: folders, trash, sort.
4. Google sign-in, Drive picker, upload. Test the unverified items first.
5. Pro backend: devices, roles, upload queue, audit log.
6. Subscription and release.

## Working rules
- Plan first. Do not write code for a phase until its open decisions are answered.
- Be truthful and accurate. Mark anything unverified. Do not invent API behavior; test it.
- Read only the files needed. Prefer exact file and line targets over broad exploration.
- Use Sonnet by default. Use a stronger model only for the Pro backend security design.
- Keep this file under 200 lines and limited to stable rules.
