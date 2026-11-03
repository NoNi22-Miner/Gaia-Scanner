# Mobile PDF/Image Converter (name TBD)

## What it is
Free mobile app, Android first. Converts PDF to JPG/PNG and JPG/PNG to PDF.
Convert only: no editing of content, no OCR, no Word conversion.
All conversion runs on the phone. Structure reference: Clear Scan by Indy Mobile (import many files, PDF import with DPI setting, PDF export without watermark, folders, trash, sorting, Drive sync). Skip its camera scanning, filters, markup, ads and subscriptions.

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
- Stack: native Android, Kotlin + Jetpack Compose. Use Android's built-in PdfRenderer (API 21+) to render pages to bitmaps and PdfDocument (API 19+) to write PDFs by drawing on each page's Canvas. Both are from the Android reference docs, not yet tested on a device.
- Platforms: Android only in v1. Decide on iOS after v1 ships (the Google Picker docs do not mention iOS).
- PC version: deferred until after mobile, not dropped. Not in the current phases. Earlier plan was Tauri or Electron with pdf.js and pdf-lib.
- Owner: one bound owner device per org. If it is lost, the owner proves identity with a TOTP code from an authenticator app (e.g. Google Authenticator) set up at enrollment, then binds a new device. Design the details in phase 5.

## Open decisions: ask the user before coding
- Owner recovery details (phase 5): the authenticator must not live only on the owner phone, or a lost phone loses both. Backup codes? What happens to the old device after re-binding? Does the owner also sign in with Google (needed for Drive anyway)?
- Member uploads: through the backend, or direct to Drive via resumable sessions created by the backend (not verified).
- Is Play billing required for invoiced company plans? Not confirmed.
- Minimum Android version (minSdk). PdfRenderer needs API 21+; check the minSdk of the Compose and other libraries chosen.

## Unverified: prototype and test before building on them
- Does drive.file cover folders the app creates?
- Can the app create files inside a folder the user picked with the Google Picker (PICKER_ALLOW_FOLDER_SELECTION)?
- How one user's app instance sees files created by another user.
- Licenses of any PDF library chosen. Read its LICENSE file.
- Password-protected PDFs: per the docs, PdfRenderer throws SecurityException for them; a password option exists only from API 35 (LoadParams). The docs also advise rendering untrusted files in an isolated process.

## Data (metadata only; files stay on the phone and in Drive)
- Settings: output format, JPEG quality, render DPI, page size and margins, output folder, theme, language, Drive sync on/off.
- Job: id, type, inputs, options, status, progress, error, times.
- Document: id, name, kind, page count, size, local path, folder id, Drive file id, created, modified, trashed at.
- Folder: id, name, parent id. Page: id, document id, order, image path, width, height.
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
