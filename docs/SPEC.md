# Phase 1 Spec: Mobile PDF/Image Converter

Status: draft for review, 2026-11-03. CLAUDE.md holds the stable rules; this file says what we build and how.

Labels used below:
- **[docs]** checked in official Android docs, not yet tested on a device.
- **[test]** unverified. Prototype it before building on it.
- **[proposal]** a suggested default. Confirm or change.
- **[open]** needs a decision from the owner of the project.

## 1. Scope

### Free v1 (phases 2 and 3)
- Import many PDFs and images at once. Inputs: PDF, JPG, PNG, plus WebP and HEIC/HEIF where the phone can decode them.
- Camera: the app's own camera screen, Single or Batch. Photos then open in the Adjust screen.
- PDF to JPG or PNG: one image per page, at a chosen DPI.
- Images to one PDF, pages in the order the user sets. Each page can be cropped (4 corners, straightened) and rotated 90° on the Adjust screen. No watermark.
- Library: folders, trash, sorting.
- Export: save to a folder on the phone, or share through the Android share sheet (Drive app, email, chat). No login.

### Pro (phases 4 to 6)
Auto-upload to a Drive folder, auto-create folders there, up to 20 devices, members can only add. See CLAUDE.md.

### Out of scope
OCR, Word, drawing or text markup on pages (other than the eraser and an optional text watermark), merging or splitting PDFs, ads.

## 2. Stack

- Kotlin, Jetpack Compose, Material 3. One activity, Navigation Compose.
- Coroutines and Flow for background work and UI state.
- Room for metadata (tables in CLAUDE.md "Data"). DataStore for Settings.
- WorkManager for conversion jobs, and for uploads from phase 4.
- androidx.activity 1.7+ for the Photo Picker [docs]. Storage Access Framework (SAF) for PDFs and the output folder.
- androidx.exifinterface to read photo orientation.
- PDF: Android's built-in PdfRenderer and PdfDocument only [docs]. No third-party PDF library in v1. If the phase 2 tests in 3.3 fail, evaluate a library and read its LICENSE before adding it.
- Dependency wiring by hand (one small app container) [proposal]. No Hilt, to keep the build simple.
- minSdk 29 (Android 10). Decided: older, low-spec phones are out (weak cameras, slow CPUs). At the first build in phase 2, confirm no chosen library needs a higher minSdk. That check could not run yet because Google Maven (dl.google.com) is blocked from the cloud sandbox.
- targetSdk: whatever Google Play requires at release time (check in phase 6).
- Build: Gradle with Kotlin DSL and a version catalog. GitHub Actions builds a debug APK on each push, so it can be installed from a phone [proposal]. Device tests run on a CI emulator plus one real phone [proposal].

## 3. Convert engine (phase 2)

### 3.1 Import
- Images: Photo Picker, `PickMultipleVisualMedia` with images only [docs]. The platform caps how many items one pick can return (`MediaStore.getPickImagesMaxLimit()`) [docs].
- Images outside the gallery (Downloads, other apps): a "Browse files" option using SAF `OpenMultipleDocuments` for JPG, PNG, WebP and HEIC/HEIF.
- WebP and HEIC/HEIF are inputs only. [test] Which of them decode on real phones from Android 10 up. A file that does not decode gets a clear "format not supported on this phone" error.
- Take photo: open the phone's camera app through the system camera intent (androidx `TakePicture` contract), writing to a file the app provides [test: full resolution comes back on several phone brands]. Hide the button if the phone has no camera app.
- PDFs: SAF `OpenMultipleDocuments` for `application/pdf`.
- Also accept files shared to the app and "Open with" [proposal].
- Every import is copied into app-private storage. Reasons:
  - Photo Picker access lasts only until the phone restarts or the app stops [docs].
  - PdfRenderer needs a seekable file descriptor [docs], and content URIs may not be seekable.
  - The library keeps working if the user deletes the original.
- The app requests no storage permissions.
- Android 10 gets the Photo Picker only through a Google Play services backport, which needs a manifest entry [docs]. Otherwise the library falls back to the system file picker [docs]. Android 11 and up have it built in, if the phone gets Google System Updates [docs].

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
1. The user puts the images in order (drag to reorder) and can rotate any page by 90°. Rotation is saved per page and applied when drawing. The source image file is not changed.
2. Decode each image downsampled to the size it will have on the page, and apply its EXIF orientation, then the user's rotation [test: photos from several phone brands].
3. Page size [proposal]: Fit to image (default: page has the image's shape, long side 842 pt), A4, or Letter. Portrait or landscape chosen per image. Margins: none, small, or large.
4. For each image: encode it as JPEG and write it as one page with our own writer (`JpegPdfWriter`), centered in the page box. Free the bitmap before the next image.
5. Finish the file (page tree, cross-reference table, trailer).

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
Tested on a CI emulator at API 29, a CI emulator at the latest API, and one real lower-end phone (4 GB RAM or less):
- A 100-page PDF converts to JPG at 150 DPI and to PNG at 300 DPI with no crash. Progress shows, and cancel works.
- 50 photos convert to one PDF in the right order and orientation, including pages rotated by the user. It opens in Chrome, Drive and Acrobat. The file size is recorded.
- A photo taken with Take photo arrives at full resolution and converts.
- HEIC and WebP inputs either convert or give the clear "not supported" error.
- Password-protected, damaged and empty files give clear errors.
- Results appear in a flat list (folders come in phase 3) and can be saved or shared.
- Every [test] item in section 3 has its result written into CLAUDE.md, as verified or failed.

### 3.7 Phase 2 as built (differences from the plan above)
- Page order uses up and down buttons per page. Drag to reorder is not built yet.
- Delete removes a document and its files for good. Phase 3 replaces it with trash.
- The PDF renderer runs in the app's own process. An isolated process is not built yet. Decide before phase 4, when Drive credentials will be on the phone.
- Unlocking (Android 15+) keeps an unlocked copy of the PDF in app storage. The password is not stored.
- Images placed in a PDF are reduced to at most 300 DPI on the page, never enlarged, and saved as JPEG at quality 90. Transparent areas become white.
- Images to PDF does not use Android's PdfDocument. Tests showed it stores photos uncompressed (10 photos, 36 MB, became a 192 MB PDF), so the app writes PDFs itself with JPEG data inside. Checked locally with Poppler (pdfinfo, pdftoppm) and on CI by reopening with PdfRenderer.
- A failed or cancelled job does not stop the jobs queued after it. Cancel is a flag the runner checks before each page.
- Long jobs run as WorkManager foreground work, type dataSync [docs]. The WorkManager docs warn that on Android 16, long-running workers can use up the app's job quota [docs].
- Device tests run on CI emulators (API 29 and 36). They use synthetic photos, so the real-phone checks in 3.6 (camera, 50 real photos, Chrome/Drive/Acrobat) are still to do.

### 3.8 Camera and Adjust (added after phase 2 review)
Decided by the project owner after testing plans:
- **In-app camera (CameraX).** Phone camera apps can stamp a watermark or time mark on photos (for example "Shot on ..."). Taking photos inside the app avoids that. [test: on real phones, confirm no watermark and full resolution]
- **Single mode:** one shot, then the Adjust screen. **Batch mode:** shoot many in a row, tap Done; all photos go into one document in shooting order, then Adjust opens for each page in turn.
- Flash: off, auto, on. Photos are saved in the orientation the phone is held (OrientationEventListener, as in the CameraX docs) [test on phones].
- **Adjust screen:** drag 4 corners onto the page edges; the app straightens that area into a rectangle with Android's Matrix.setPolyToPoly. Rotate turns the page 90°. Reset, Next, Skip. Tapping a page in a document opens it too. The rotate button left the page list.
- The crop is stored per page as 4 corner fractions of the upright image (pages.crop, database version 2 with a migration from version 1). Images to PDF applies crop, then rotation.
- No automatic edge detection: that would need an image library or ML model and a license check.

### 3.9 Page editor and Enhance (phase 2b, from the ClearScanner review on 2026-11-04)
The owner reviewed ClearScanner's screens and adopted its crop, enhance, PDF-tool, library and menu functions. Phase 2b builds the page editor; the rest is placed in phases 3, 4 and 6 (see CLAUDE.md).
- **Flow:** camera photos open the editor in Crop mode with corners found automatically; Done goes to Enhance mode; Done goes to the next page. Tapping a page in a document opens the editor in Enhance mode.
- **Crop mode:** 4 corner handles and 4 edge handles (move a whole side), Auto (detect again), Full frame, rotate left, rotate right.
- **Enhance mode:** filters Original, Photo, Clear, Docs, Color; brightness and contrast sliders; Crop, rotate left/right, Eraser.
- **Eraser:** brush strokes in two modes: White (paint white) and Smart fill (OpenCV inpaint from the surroundings), brush size, undo. Strokes are stored in page coordinates after crop and rotation, so changing the crop or rotation clears them.
- **Menu:** Process All (save this page, then give every remaining page auto corners and this page's filter, brightness and contrast), Discard (remove this page), Discard All (remove every page in this editing run).
- **Non-destructive:** the original photo is kept. Each page stores crop, rotation, filter, brightness, contrast and eraser strokes (database version 3). Thumbnails and PDFs render them in this order: crop, rotate, erase, brightness/contrast, filter.
- **Edge detection (OpenCV):** image scaled to 800 px; Canny at three thresholds plus Otsu, contours simplified to 4-point convex shapes of at least 15% of the image; each scored by edge strength along its outline times its area; the whole frame is ignored. Prototyped with opencv-python on synthetic scenes (wood, light-gray and dark backgrounds, uneven light: corners within 0.4%) and on the owner's ClearScanner screenshot.
- **Filters (OpenCV):** Clear divides each colour channel by a blurred estimate of the paper (shadow removal), then deepens text; Docs is Clear in gray with an Otsu threshold (no speckles in the prototype, unlike adaptive threshold); Color is Clear with 1.5x saturation; Photo is a mild contrast and saturation boost.
- [test] Edge detection on real photos (busy desks, white paper on white tables), filter speed at full page size on low-end phones, smart-fill quality on colored paper.

## 4. Library (phase 3)

- Folders: nested (parent id). Create, rename, move documents. Deleting a folder moves its contents to trash.
- Trash: soft delete (trashed at). Restore, delete forever, empty trash. Items are deleted for good after 30 days [proposal].
- Sort by name, date created, date modified or size, ascending or descending. The choice is remembered [proposal].
- Search by name [proposal].
- Document detail: page thumbnails, info (pages, size, dates), and actions: convert, save, share, move, rename, trash.

Screens:
1. Library (home): folder path, list or grid, sort menu, add button (Import PDFs / Import images / Take photo).
2. Document detail.
3. Convert options: format, DPI and quality, or page size and margins.
4. Image order and rotate (for images to PDF).
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

Answered in phases 1 and 2: minSdk 29; inputs include WebP and HEIC; 90° rotate; in-app camera (Single/Batch); 4-corner crop with auto edge detection (OpenCV); filters, brightness/contrast, eraser; the ClearScanner functions adopted on 2026-11-04 and placed in phases (CLAUDE.md).

Before phase 3 code (ask the owner):
1. Trash: delete for good after 30 days [proposal], or keep until emptied?
2. Sort: options name, date created, date modified, size; remembered for the whole library or per folder?
3. Search by name: in phase 3 or later?
4. Library view: list or grid by default? What each item shows (thumbnail, name, size, page count, date).
5. Select many: which actions (share, move to folder, trash, convert)? Merging documents is still out of scope unless the owner adds it.
6. Home screen: Gallery and Camera buttons at the bottom (like ClearScanner). Do Import PDF, Import images and New folder move to the top-right menu?
7. Text watermark: text, font, size, color, opacity, angle, single or tiled pattern? Applied only when making or sharing a PDF, or stored per document and shown in previews? Never on by default.
8. Reduce file size: presets (for example High, Medium, Small) or a target size? Replace the PDF, or save a smaller copy? Show the new size before saving.
9. New blank page: size (same as the document, A4 or Letter) and where it goes (end, or after the current page).
10. Side menu: Settings, Trash, Tutorial, Feature request / bug report, About. What the Tutorial shows (a few swipe screens?). The support email stays a placeholder until release.
11. Deleting a folder: its documents go to trash (spec section 4). Confirm.

Later phases: isolated process for PdfRenderer (decide before phase 4); free manual Drive upload and Backup/Restore format (phase 4); member upload path, rule 4 scope, owner recovery details and hosting (phase 5); Play billing for invoiced plans (phase 6).
