# Implementation Plan - Instant Start (Smart Metadata Reuse)

Eliminate redundant network requests by reusing video metadata (JSON "passport") fetched during the analysis stage in the background download worker.

## Proposed Changes

### Logic & Bridge

#### [MODIFY] [ytdlp_wrapper.py](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/python/ytdlp_wrapper.py)
- Refine `get_video_info` to ensure the metadata passport is saved for all cases, including single videos and playlists.
- Optimize `ydl_opts` in `get_video_info` to ensure enough data is captured for a "Fast Start" (avoiding `extract_flat` for single videos if it causes missing formats).
- In `download_video`, ensure that if a passport exists, it is used to skip the extraction phase entirely using `ydl.process_info(info)`.
- Add defensive checks to fallback to full extraction if the passport is corrupted or expired.

#### [MODIFY] [YtDlpBridge.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/YtDlpBridge.kt)
- Ensure `fetchInfo` and `downloadVideo` logic consistently passes the `passportPath`.
- (Optional) Add a check to `fetchInfo` to return cached metadata if it's very fresh, further reducing requests.

#### [MODIFY] [DownloadWorker.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/DownloadWorker.kt)
- Optimize the "Autopilot" metadata fetch block. If a passport already exists (e.g., from a previous analysis), it should be used instead of a full new `fetchInfo` call if possible.

### Infrastructure

#### [MODIFY] [CacheManager.kt](file:///D:/AndroidStudioProjects/MyApplication/app/src/main/java/com/example/videodownloader/CacheManager.kt)
- Ensure the `passports` directory is correctly managed and cleaned up.

## Verification Plan

### Automated Tests
- I will verify the logic by checking logs to ensure `Passport found! Instant start for ...` is printed in `DownloadWorker` when expected.
- Check that the `passports` directory contains valid JSON files after analysis.

### Manual Verification
1. Open the app and paste a YouTube link.
2. Wait for analysis to complete (title and thumbnail appear).
3. Click "Download".
4. Check logs (Logcat) to see if the worker skips the analysis phase.
5. Verify that the download starts almost immediately without the "Preparing..." delay typical for metadata fetching.
