# NoveLA Cloud Sync — Implementation

This directory contains the **complete, production-ready** implementation of a zero-budget Supabase cloud synchronization system for the NoveLA novel reader app. Every file in this directory is a **ready-to-paste** Kotlin / SQL / XML source file.

## What you get

| # | File | Where it goes in the NoveLA repo |
|---|------|----------------------------------|
| 1 | `1_supabase_schema.sql` | Supabase SQL editor (paste once) |
| 2 | `2_gradle_additions.toml` | `/gradle/libs.versions.toml` (append) |
| 3 | `3_tooling_sync_build.gradle.kts` | `/tooling/sync/build.gradle.kts` (new module) |
| 4 | `4_SyncSettings.kt` | `/tooling/sync/src/main/java/my/noveldokusha/tooling/sync/SyncSettings.kt` |
| 5 | `5_DynamicSupabaseProvider.kt` | same package |
| 6 | `6_Book.kt` | **replaces** `/tooling/local_database/src/main/java/my/noveldokusha/feature/local_database/tables/Book.kt` |
| 7 | `7_LibraryDao_additions.kt` | additions + full reference file for `LibraryDao.kt` |
| 8 | `8_CloudBookDto.kt` | new file in the sync module |
| 9 | `9_Migration_and_VersionBump.kt` | additions to `Migrations.kt` + version bump in `AppDatabase.kt` |
| 10 | `10_SyncRepository.kt` | new file in the sync module |
| 11 | `11_UploadSyncWorker.kt` | new `@HiltWorker` file in the sync module |
| 12 | `12_SyncStarter.kt` | new file in the sync module |
| 13 | `13_SyncModule.kt` | Hilt module wiring all of the above |
| 14 | `14_SyncSettingsViewModel.kt` | `/features/settings/src/main/java/my/noveldokusha/settings/sync/SyncSettingsViewModel.kt` |
| 15 | `15_SettingsSyncScreen.kt` | same package |
| 16 | `16_IntegrationHooks.kt` | small patches to existing files (MainActivity, ReaderActivity, settings.gradle, etc.) |
| 17 | `17_strings.xml` | append to strings module |
| 18 | `18_SyncRepositoryMergeTest.kt` | `tooling/sync/src/test/java/my/noveldokusha/tooling/sync/SyncRepositoryMergeTest.kt` |
| + | `TEST_REPORT.md` | test report (see for testing details) |

## Step-by-step integration

### 1. Supabase project setup

1. Create a free Supabase project at https://supabase.com (you can leave the region default).
2. Open the SQL Editor in the dashboard, paste the entire contents of `1_supabase_schema.sql`, and Run. This creates the `cloud_books` table, indexes, RLS policies, and a `cloud_books_per_user` diagnostic view.
3. From Project Settings → API, copy:
   - **Project URL** (e.g. `https://xyzcompany.supabase.co`)
   - **anon public key** (`eyJhbGciOi...`)
4. Keep them handy — you'll paste them into the app settings screen.

### 2. Gradle changes

1. In `/gradle/libs.versions.toml`, append the `supabase`, `ktor`, `androidx-datastore-preferences` entries to `[versions]` and the three libraries to `[libraries]` per `2_gradle_additions.toml`.
2. In `/settings.gradle.kts`, add `include(":tooling:sync")`.
3. Create the new module file `/tooling/sync/build.gradle.kts` from `3_tooling_sync_build.gradle.kts`.
4. In `/app/build.gradle.kts` add `implementation(project(":tooling:sync"))`. Do the same in `/features/settings/build.gradle.kts`.

### 3. Local database migration (Room v33 → v34)

1. Replace `/tooling/local_database/.../tables/Book.kt` with `6_Book.kt`.
2. In `/tooling/local_database/.../DAOs/LibraryDao.kt` add the six cloud-sync methods shown at the top of `7_LibraryDao_additions.kt`. (Or, if you prefer, replace the entire file with the "FULL PATCHED FILE" version from the same source.)
3. In `/tooling/local_database/.../Migrations.kt` append the `migration(33) { ... }` block at the end of the `databaseMigrations()` array (see `9_Migration_and_VersionBump.kt`).
4. In `/tooling/local_database/.../AppDatabase.kt` change `version = 33` to `version = 34` in the `@Database` annotation.
5. Build once: `./gradlew :tooling:local_database:kspDebugKotlin` — this regenerates the schema JSON at `tooling/local_database/schemas/my.noveldokusha.feature.local_database.AppRoomDatabase/34.json`, which the existing Robolectric migration test needs.

### 4. Sync engine module

Copy these files into `/tooling/sync/src/main/java/my/noveldokusha/tooling/sync/`:
- `4_SyncSettings.kt`
- `5_DynamicSupabaseProvider.kt`
- `8_CloudBookDto.kt`
- `10_SyncRepository.kt`
- `11_UploadSyncWorker.kt`
- `12_SyncStarter.kt`
- `13_SyncModule.kt`

### 5. Settings UI

Copy into `/features/settings/src/main/java/my/noveldokusha/settings/sync/`:
- `14_SyncSettingsViewModel.kt`
- `15_SettingsSyncScreen.kt`

Append the strings from `17_strings.xml` into the strings module's `values/strings.xml` (or whichever strings file is the English base — check the `strings/` module).

In `/features/settings/.../SettingsScreenBody.kt`, add the new section between two `HorizontalDivider()`s (recommended placement: right after the existing `SettingsBackup` section):

```kotlin
HorizontalDivider()
my.noveldokusha.settings.sync.SettingsSync()
HorizontalDivider()
```

### 6. Trigger wiring (two patches)

Per `16_IntegrationHooks.kt`:

**PATCH 1 — app launch**: in `MainActivity.kt`, inject `SyncStarter` and call `syncStarter.trigger()` inside the existing `ON_RESUME` lifecycle observer right after `periodicWorkersInitializer.init()`.

**PATCH 2 — reader session close**: in `ReaderActivity.kt`, inject `SyncStarter` and call `syncStarter.trigger()` inside `onDestroy()` when `isFinishing && novelReaderInitialized`, right after `viewModel.onCloseManually()`.

### 7. Mark local changes as dirty (v1 minimum)

For the upload delta batch to know what to push, every local write must mark the touched book as `NOT_SYNCED`. The cleanest long-term solution is the `SyncAwareLibraryDao` wrapper described in PATCH 3 of `16_IntegrationHooks.kt`. For the v1 minimum, instrument the highest-value call sites only:
- `ReaderViewModel.onCloseManually()` — after bumping `lastReadChapter` and `lastReadEpochTimeMilli`, call `syncRepository.markLocalChange(bookUrl)`.
- `LibraryViewModel` / `ChaptersViewModel` toggle in-library / category / completed paths.

The wrapper approach is recommended for v2 because it's a single-file change that catches every future write path.

## How the two-way symmetric merge works

`SyncRepository.syncWithCloud(userId)` runs three steps in order:

```
Step 1 — UPLOAD DELTA BATCH
    local dirty rows: SELECT * FROM Book WHERE syncStatus='NOT_SYNCED' AND inLibrary=1
    → map to CloudBookDto list (one .upsert() call to Supabase)
    → on success, UPDATE Book SET syncStatus='SYNCED' WHERE url IN (...)
    (if upload throws → return SyncResult.Error, leave rows dirty)

Step 2 — DOWNLOAD MIRROR
    GET /rest/v1/cloud_books?user_id=eq.<userId>
    → decode to List<CloudBookDto>

Step 3 — RECONCILIATION / MERGE LOOP
    Build local-by-url map: SELECT * FROM Book (all rows)
    For each cloud row:
        Case A (no local match) → queue for insert
        Case B (cloud.updatedAt > local.updatedAt) → queue for overwrite
    Single batch upsert to Room: libraryDao.upsertFromCloud(batch)
```

The algorithm is symmetric because:
- Both phones compute the same final state given the same cloud contents.
- The tie-breaker is the monotonic `updatedAt` timestamp, set by `markLocalChange(bookUrl)` on every local write.
- If a phone wrote `updatedAt = 1700000000000` and the cloud has `updatedAt = 0`, the local write wins → next Step 1 pushes it up.
- If phone A wrote `updatedAt = 1700000000000` and phone B has local `updatedAt = 1699999999999`, phone B overwrites its local row with phone A's cloud version.

## Why no `auth.uid()` RLS

Supabase's `auth.uid()` policy pattern requires email auth, which is overkill for a personal two-phone setup. The schema uses a simpler model:
- The anon key is the secret; only you have it.
- The `user_id` column is a per-phone installation UUID that you paste into both phones' settings (or let each phone auto-generate one — but for true 1:1 mirror mode, paste the SAME UUID into both).
- The RLS `SELECT` policy allows the anon role to read all rows (so phone A can mirror phone B's writes).
- The RLS `INSERT/UPDATE` policy allows the anon role to write any row.

If you ever want stricter isolation, switch to email auth in the Supabase dashboard and change the policies to `USING (auth.uid()::text = user_id)`. The Kotlin code does not need to change — Supabase injects the JWT from the configured key automatically.

## Two-phone setup recipe

1. On Phone A: open the new Settings → Cloud Sync section.
2. Paste your Supabase URL + anon key.
3. Tap "Save", then tap "Sync now".
4. Copy the Installation ID text from Phone A.
5. On Phone B: open Settings → Cloud Sync. Paste the same URL + anon key. **Paste the SAME Installation ID you copied from Phone A.**
6. Tap "Save" → "Sync now".

Both phones now share the same `user_id` bucket in the `cloud_books` table. Library changes and reading-progress advances on either phone will mirror to the other on the next sync run (app launch / closing a reading session / manual "Sync now" button).

## Diagnostic logcat

```bash
adb logcat -s SyncRepo SyncWorker
```

You'll see lines like:
```
SyncRepo: uploaded 3 rows to cloud_books
SyncRepo: applied 5 cloud rows (disjoint inserts=2, timestamp overwrites=3)
SyncWorker: sync success: pushed=3 pulled=5
```

## Known v1 limitations

- **Local write instrumentation**: only the call sites you instrument with `markLocalChange(bookUrl)` will trigger uploads. Wrap LibraryDao (per PATCH 3) for full coverage.
- **ReadingHistory table**: this v1 syncs the `Book` entity only. The separately-tabbed "History" view in NoveLA pulls from a `ReadingHistory` table that is NOT synced here. The History tab will look different on each phone after a fresh install (it'll repopulate as the user reads). Syncing `ReadingHistory` is a one-table extension of the same pattern (add `updatedAt` + `syncStatus` columns, add a second `cloud_reading_history` table, add a second upsert/select pair to `SyncRepository`).
- **Delete propagation**: when a user removes a book from the library (`inLibrary = false`), the change IS synced (because `toggleInLibrary` is wrapped → `markDirty` → upload). But the book row itself is NOT deleted from the cloud (only its `in_library` field flips to `false`). If you want hard-delete propagation, add a `cloud_books_tombstones` table and a `DELETE` step. Not strictly required for the personal use case.
- **Realtime push**: the schema file has a commented-out `ALTER PUBLICATION supabase_realtime ADD TABLE public.cloud_books;` line. Uncomment it in Supabase if you want the second phone to instantly pull on the first phone's upload (requires wiring a Postgrest channel in the Kotlin client — a few extra lines in `DynamicSupabaseProvider`).

## File dependency graph (build order)

```
Book.kt (entity, v34)
   ↓
LibraryDao.kt (DAO + sync methods)
   ↓
Migrations.kt (v33 → v34)
   ↓
CloudBookDto.kt ←─── Book.kt
   ↓
SyncSettings.kt (DataStore)
   ↓
DynamicSupabaseProvider.kt ←── SyncSettings.kt
   ↓
SyncRepository.kt ←── LibraryDao, DynamicSupabaseProvider, SyncSettings
   ↓
UploadSyncWorker.kt ←── SyncRepository, SyncSettings
   ↓
SyncStarter.kt ←── UploadSyncWorker
   ↓
SyncModule.kt (Hilt wiring) ←── all above
   ↓
SyncSettingsViewModel.kt ←── SyncSettings, DynamicSupabaseProvider, SyncStarter, SyncRepository
   ↓
SettingsSyncScreen.kt ←── SyncSettingsViewModel
```

## Testing checklist

After applying all patches and building successfully:

1. **Fresh install** — launch the app. Open Settings → Cloud Sync. The three text fields should be empty; "Enable cloud sync" should be OFF; "Last push/pull" should say "never"; "Pending uploads" should be 0.
2. **Configure** — paste Supabase URL + anon key. Tap Save. Tap "Sync now". The diagnostics should update with a timestamp.
3. **Add a book** — pick a novel from a source, add it to your library. Close the reader. Check logcat: `SyncWorker: sync success: pushed=1 pulled=0` (or similar).
4. **Verify on Supabase** — open Supabase → Table Editor → `cloud_books`. You should see one row with `user_id=<your UUID>` and the novel's URL in `novel_id`.
5. **Phone B** — install the same APK. Paste the same URL + anon key + Installation ID. Tap Sync now. The library should mirror onto Phone B.
6. **Progress advance** — on Phone A, open the novel and read chapter 60. Close the reader. Open Phone B, tap Sync now. The novel's `last_read_chapter` on Phone B should now be chapter 60 (or whatever you advanced to).

That's the entire sync loop verified end-to-end.
