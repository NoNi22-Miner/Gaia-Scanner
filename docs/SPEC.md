# Phase 1 Spec: Mobile PDF/Image Converter

Status: draft for review, 2026-11-03. CLAUDE.md holds the stable rules; this file says what we build and how.

Labels used below:
- **[docs]** checked in official Android docs, not yet tested on a device.
- **[test]** unverified. Prototype it before building on it.
- **[proposal]** a suggested default. Confirm or change.
- **[open]** needs a decision from the owner of the project.

## 1. Scope

### Free v1 (phases 2 and 3)
- Import many PDFs and images at once.
- PDF to JPG or PNG: one image per page, at a chosen DPI.
- JPG/PNG to one PDF, pages in the order the user sets. No watermark.
- Library: folders, trash, sorting.
- Export: save to a folder on the phone, or share through the Android share sheet (Drive app, email, chat). No login.

### Pro (phases 4 to 6)
Auto-upload to a Drive folder, auto-create folders there, up to 20 devices, members can only add. See CLAUDE.md.

### Out of scope
Camera scanning, filters, markup, OCR, Word, editing page content, merging or splitting PDFs, ads.

## 2. Stack

- Kotlin, Jetpack Compose, Material 3. One activity, Navigation Compose.
- Coroutines and Flow for background work and UI state.
- Room for metadata (tables in CLAUDE.md "Data"). DataStore for Settings.
- WorkManager for conversion jobs, and for uploads from phase 4.
- androidx.activity 1.7+ for the Photo Picker [docs]. Storage Access Framework (SAF) for PDFs and the output folder.
- androidx.exifinterface to read photo orientation.
- PDF: Android's built-in PdfRenderer and PdfDocument only [docs]. No third-party PDF library in v1. If the phase 2 tests in 3.3 fail, evaluate a library and read its LICENSE before adding it.
- Dependency wiring by hand (one small app container) [proposal]. No Hilt, to keep the build simple.
- minSdk [open]: the higher of 21 (PdfRenderer) and the highest minSdk among the chosen libraries. Fix the number when the project first builds in phase 2. Library manifests could not be checked from the cloud sandbox because Google Maven (dl.google.com) is blocked there.
- targetSdk: whatever Google Play requires at release time (check in phase 6).
- Build: Gradle with Kotlin DSL and a version catalog. GitHub Actions builds a debug APK on each push, so it can be installed from a phone [proposal]. Device tests run on a CI emulator plus one real phone [proposal].

## 3. Convert engine (phase 2)

### 3.1 Import
- Images: Photo Picker, `PickMultipleVisualMedia` with images only [docs]. The platform caps how many items one pick can return (`MediaStore.getPickImagesMaxLimit()`) [docs].
- Images outside the gallery (Downloads, other apps): a "Browse files" option using SAF `OpenMultipleDocuments` for `image/jpeg` and `image/png`.
- PDFs: SAF `OpenMultipleDocuments` for `application/pdf`.
- Also accept files shared to the app and "Open with" [proposal].
- Every import is copied into app-private storage. Reasons:
  - Photo Picker access lasts only until the phone restarts or the app stops [docs].
  - PdfRenderer needs a seekable file descriptor [docs], and content URIs may not be seekable.
  - The library keeps working if the user deletes the original.
- The app requests no storage permissions.
- Android 4.4 to 10 get the Photo Picker only through a Google Play services backport, which needs a manifest entry [docs]. Otherwise the library falls back to the system file picker [docs].

### 3.2 PDF to images
1. Open the private copy with PdfRenderer, off the main thread.
2. For each page (only one page can be open at a time [docs]):
   - Pixel size = round(points × DPI / 72). Page size is in points, 1/72 inch [docs].
   - Bitmap must be ARGB_8888 [docs]. Fill it white before rendering [test: the docs do not say what the page background is, and JPG has no transparency].
   - Render with `RENDER_MODE_FOR_DISPLAY` [test: compare with `RENDER_MODE_FOR_PRINT`].
   - Encode as JPG (quality from Settings) or PNG, write the file, free the bitmap.
3. Result: a new image document with one Page row per page. It is also copied to the output folder if one is set.

DPI choices [proposal]: 72, 150 (default), 200, 300, 600.

Memory: an A4 page (595 × 842 pt) at 300 DPI is 2480 × 3508 px, about 35 MB as a bitmap. At 600 DPI it is about 139 MB. [proposal] Cap each page at 16 megapixels (about 64 MB). Pages over the cap render at a lower DPI, and the job result says so. Tune the cap on a low-RAM phone.

Password-protected PDFs:
- Below Android 15 (API 35), PdfRenderer throws SecurityException [docs]. Show: "Password-protected PDFs are not supported on this Android version."
- API 35 and up: ask for the password and open with `LoadParams` [docs].

Untrusted files: the docs advise rendering them in an isolated process [docs]. [proposal] Render in a separate process. Decide between an isolated process and a plain one in phase 2, after a prototype.

### 3.3 Images to PDF
1. The user puts the images in order (drag to reorder).
2. Decode each image downsampled to the size it will have on the page, and apply its EXIF orientation [test: photos from several phone brands].
3. Page size [proposal]: Fit to image (default: page has the image's shape, long side 842 pt), A4, or Letter. Portrait or landscape chosen per image. Margins: none, small, or large.
4. For each image: `PageInfo.Builder(width, height, number)` in points [docs], draw the bitmap centered on the page canvas, finish the page, free the bitmap.
5. Write the document to a file and close it [docs].

Must test before building on it:
- [test] How PdfDocument stores bitmaps. Convert 20 phone photos and compare the PDF size with the inputs. If the PDF is much bigger, first try downscaling to about 200 DPI on the page. If it is still too big, evaluate a PDF library that embeds JPEG data directly (license check required).
- [test] Memory with 100+ pages: does PdfDocument keep every page in memory until it writes the file?
- [test] The output opens correctly in Chrome, the Google Drive viewer, Adobe Acrobat, and PdfRenderer itself.

### 3.4 Jobs
- Each conversion is a Job row: type, inputs, options, status (queued, running, done, failed, cancelled), progress, error, times.
- Jobs run in WorkManager, so a batch continues if the user leaves the app.
- Long batches need foreground work with a notification [test: confirm WorkManager's time limits and which foreground service type Android 14+ requires].
- One job runs at a time, to limit memory [proposal].
- Cancel stops after the current page and deletes partial outputs.
- If one file fails, the job moves on to the next. The result lists the files that failed.
- Errors are shown in plain words: damaged file, password, out of space, page too large.

### 3.5 Output and names
- Outputs live in app-private storage as part of the library.
- Save to phone: SAF `CreateDocument` for one file, `OpenDocumentTree` to save many images into a folder.
- Share: the Android share sheet, through a FileProvider.
- Settings, Output folder (optional): pick a folder once with a persistable permission, and every result is also written there [proposal].
- Names [proposal]:
  - PDF to images: `<pdf name>_p001.jpg`
  - Images to PDF: `<first image name>_<yyyyMMdd-HHmm>.pdf`
  - On a name clash, add ` (2)`, ` (3)`, and so on.

### 3.6 Phase 2 is done when
Tested on a CI emulator at minSdk, a CI emulator at the latest API, and one real phone with 4 GB RAM or less:
- A 100-page PDF converts to JPG at 150 DPI and to PNG at 300 DPI with no crash. Progress shows, and cancel works.
- 50 photos convert to one PDF in the right order and orientation. It opens in Chrome, Drive and Acrobat. The file size is recorded.
- Password-protected, damaged and empty files give clear errors.
- Results appear in a flat list (folders come in phase 3) and can be saved or shared.
- Every [test] item in section 3 has its result written into CLAUDE.md, as verified or failed.

## 4. Library (phase 3)

- Folders: nested (parent id). Create, rename, move documents. Deleting a folder moves its contents to trash.
- Trash: soft delete (trashed at). Restore, delete forever, empty trash. Items are deleted for good after 30 days [proposal].
- Sort by name, date created, date modified or size, ascending or descending. The choice is remembered [proposal].
- Search by name [proposal].
- Document detail: page thumbnails, info (pages, size, dates), and actions: convert, save, share, move, rename, trash.

Screens:
1. Library (home): folder path, list or grid, sort menu, add button (Import PDFs / Import images).
2. Document detail.
3. Convert options: format, DPI and quality, or page size and margins.
4. Image order (for images to PDF).
5. Jobs: progress and failures.
6. Trash.
7. Settings.
8. Later: Account and Drive (phase 4), Organization and devices (phase 5).

## 5. Google Drive (phase 4): test first

Before any feature code, prototype these and record the answers in CLAUDE.md:
1. [test] Does drive.file cover folders the app creates?
2. [test] Does the Google Picker work in an Android app at all, and can the app create files inside a folder picked with it? Fallback if not: the app creates its own folder and the user picks from the app's folders.
3. [test] How does one user's app instance see files created by another user?
4. [test] Under drive.file, can the Android app see files the backend created, if both use OAuth clients in the same Google Cloud project? (Needed for phase 5.)

Then:
- Sign-in and Drive authorization: use Google's current Android APIs. Check the docs at the start of phase 4.
- Uploads run in WorkManager, only with network, retrying with backoff. One SyncRecord per document. Large files use resumable upload.
- Library folders are mirrored as Drive subfolders (Pro rule 2).
- Signing out stops uploads and keeps local files.
- [open] Is a manual in-app "Upload to Drive" free, or Pro only? Free users can always use the share sheet.

## 6. Pro backend (phase 5): outline only

Per CLAUDE.md, the security design happens in phase 5 with a stronger model. This section only fixes the shape.
- The owner device holds the Drive authorization. The owner's folder is shared with nobody.
- Members join with an invite (code or QR) [proposal]. The backend enforces the limit of 20 devices.
- Members submit files, and the backend writes them to the owner's Drive. [open] Through the backend, or directly to Drive through a resumable session the backend creates?
- The backend enforces rule 4: member credentials can only create uploads. Delete and rename work only from the owner device.
- [open] Does rule 4 also lock a member's own library on their phone, or only what they uploaded? [proposal] Only uploaded org files.
- The audit log records every enrollment, upload, delete, role change, device removal and owner recovery.
- Owner recovery uses a TOTP code. Details are open (see CLAUDE.md).
- [open] Hosting: for example Supabase or Vercel (both are connected to this workspace), or something else.

## 7. Subscription and release (phase 6)

- Pro sold as a Google Play subscription [proposal].
- [open] Invoiced company plans: check Play's billing rules in phase 6.
- Release checklist: privacy policy, Play Data safety form, OAuth consent screen [test: what verification drive.file needs], targetSdk requirement, final app name.

## 8. Open decisions

Before phase 2 code:
1. minSdk rule in section 2.
2. Input formats: JPG and PNG only, or also WebP and HEIC where the phone can decode them?
3. Is rotating an image 90° before making a PDF allowed, or does it count as editing?
4. Build access: allow dl.google.com in the cloud environment, or build only in GitHub Actions.

Later phases: free manual Drive upload (phase 4), member upload path, rule 4 scope, owner recovery details and hosting (phase 5), Play billing for invoiced plans (phase 6).
