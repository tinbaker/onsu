# Kế hoạch sửa Onsu — từ "chạy được" đến "đúng"

> Tài liệu này là kế hoạch thực thi, không phải báo cáo. Mỗi phase đều có tiêu chí nghiệm thu rõ ràng và có thể dừng lại giữa chừng mà không để lại code nửa vời.
> Không đụng vào `implementation_plan.md` (tài liệu OTP đăng nhập của bạn).

---

## 0. Chẩn đoán trước khi lập kế hoạch

### 0.1. Không thể build trong môi trường hiện tại

Kiểm tra thực tế:

| Thứ cần | Trạng thái |
|---|---|
| `gradlew` / `gradle/wrapper/gradle-wrapper.jar` | **Không có** (chỉ có file `.properties`) |
| Gradle hệ thống | Không có |
| Android SDK (`ANDROID_HOME`) | Trống |
| `local.properties` | Không có |
| `debug.keystore` (bắt buộc bởi `signingConfigs.debugConfig`) | Không có, đã bị gitignore |
| JDK | 23.0.2 — AGP 9.1.1 thường chỉ chấp nhận JDK 17–21 |

⇒ Sửa code thì được, nhưng **không thể biên dịch kiểm chứng tại đây** nếu chưa dựng toolchain. Đây là quyết định bạn phải chốt ở mục 1.

### 0.2. Tám vấn đề sẽ xử lý, xếp theo mức chặn

| # | Vấn đề | Vị trí | Mức |
|---|---|---|---|
| B1 | Mỗi playlist chỉ giữ 1 bài (PK `(playlistId, position)` + luôn truyền `position=0`) | `MainActivity.kt:523,530,545,553` + `Daos.kt:47-61` | Chặn chức năng |
| B2 | Hết 20 bài là dừng phát (queue nạp thêm nhưng player không nhận) | `QueueManager.kt:172-192` | Chặn chức năng |
| B3 | Không lưu được phiên nghe (`onDispose` + `rememberCoroutineScope` bị cancel) | `MainActivity.kt:225-250` | Mất dữ liệu |
| B4 | Restore session tính index rồi bỏ đó, không set queue | `MainActivity.kt:214-219` | Chết code |
| B5 | Quét MediaStore + gọi Audius 2 lần lúc mở app | `MainActivity.kt:175-177,198` | Hiệu năng |
| B6 | `syncQueue` lấy index của player làm index của queue → lệch sau head-trim | `PlayerConnection.kt:138-145` | Sai logic |
| B7 | ~3.660 dòng code chết `com.example` vẫn được biên dịch | `app/src/main/java/com/example/` | Nợ kỹ thuật |
| B8 | Dependency thừa (retrofit, moshi, logging-interceptor, media3-ui, firebase-ai, credentials) | `app/build.gradle.kts` | Nợ kỹ thuật |

---

## 1. Ba quyết định cần bạn chốt trước khi tôi gõ dòng đầu tiên

### Q1. Build & kiểm chứng bằng cách nào?

| | Cách | Ưu | Nhược |
|---|---|---|---|
| **A** | Tôi sửa code, **bạn build trong Android Studio** | Nhanh, bạn thấy kết quả ngay trên máy thật | Vòng phản hồi chậm, tôi không tự phát hiện lỗi biên dịch |
| **B** | Tôi dựng toolchain tại đây (Gradle 9.3.1 + cmdline-tools + platform-36 + JDK 17, ~3 GB) | Tôi tự `assembleDebug` và chạy unit test được | Tải lâu, cần mạng ổn, cài vào máy bạn |
| **C** | Chỉ sửa code, không build | Nhanh nhất | Rủi ro lỗi biên dịch cao — **không khuyến nghị** |

Đề xuất: **B** nếu bạn muốn tôi tự verify; **A** nếu bạn đang có Android Studio mở sẵn.

### Q2. Xoá `com.example` (B7)?

Đề xuất: **xoá thẳng**, không giữ nhánh. Toàn bộ ý tưởng hay trong đó (mock data, `SeedData`, synthesizer) đã được thay bằng bản thật. Nếu bạn muốn giữ để tham khảo, tôi chuyển vào `legacy/` và loại khỏi source set.

### Q3. Phạm vi đợt này?

- **Gói "ổn định"** (Phase 1–3): dọn dẹp + sửa hết bug chặn chức năng + đồng bộ queue↔player. Đây là mức tối thiểu để app dùng được thật.
- **Gói "hoàn chỉnh"** (thêm Phase 4): lưu hành vi nghe vào Room để gu nhạc không reset mỗi lần mở app.
- **Gói "kiến trúc"** (thêm Phase 5): tách ViewModel. Rủi ro cao, nên làm riêng một đợt.

Đề xuất: làm **1 → 3** trước, rồi bạn nghiệm thu bằng tay, sau đó mới bàn 4–5.

---

## 2. Phase 1 — Dọn xác chết (không đổi hành vi)

**Mục tiêu:** giảm bề mặt code, không thay đổi bất kỳ hành vi nào người dùng thấy được. Nếu phase này gây lỗi, nghĩa là có thứ đang phụ thuộc vào code chết — và ta cần biết.

### 1.1. Xoá package `com.example`
- Xoá `app/src/main/java/com/example/` (4 file, 3.664 dòng).
- Xoá `app/src/test/java/com/example/` (2 file) và `app/src/androidTest/java/com/example/` (1 file).
- Kiểm chứng: `grep -r "com.example" app/src` → 0 kết quả; `AndroidManifest` chỉ trỏ `.MainActivity` của `com.onsu`.

### 1.2. Xoá dependency không có một dòng sử dụng
Đã grep xác nhận **0 chỗ dùng**:
- `retrofit`, `converter-moshi`, `moshi-kotlin`, `moshi-kotlin-codegen` (+ plugin `ksp` moshi codegen)
- `logging-interceptor`
- `media3-ui` (không có `PlayerView` nào)
- `firebase-ai`, `firebase-appcheck-recaptcha`, `firebase-appcheck-debug`, `firebase-bom`

Xoá khỏi `gradle/libs.versions.toml` + `app/build.gradle.kts`.

> Giữ lại `androidx.credentials` + `googleid`: nếu sau này làm Google Sign-In thật thì cần, mà chúng vô hại. Nhưng **xoá 2 import chết** trong `Screens.kt:87-88`.

### 1.3. Sửa cấu hình sai
- `AndroidManifest.xml:35`: `PlaybackService` → `android:exported="false"` (hiện đang mở service cho app ngoài bind).
- `Models.kt:9-13`: xoá field `bonus` khỏi `TasteLevel` — khai báo −0.15/0/+0.20 nhưng `ScoringEngine` không đọc; gu nhạc đã được thể hiện bằng quota 3 tầng. Giữ lại chỉ gây hiểu nhầm.
- `MainActivity.kt:212`: `syncLikedTracks(repository.getTasteSeeds().first.let { likedIds })` → `syncLikedTracks(likedIds)`.

**Nghiệm thu Phase 1:** app vẫn chạy y như trước; APK nhỏ hơn đáng kể; không còn cảnh báo import thừa.

---

## 3. Phase 2 — Sửa bốn bug chặn chức năng

### 2.1. B1 — Playlist chỉ giữ 1 bài

**Nguyên nhân:** `playlist_items` có `primaryKeys = ["playlistId", "position"]`, insert dùng `REPLACE`, mà cả 4 call site đều truyền `position = 0`.

**Cách sửa:**
1. `Daos.kt` — thêm:
   ```kotlin
   @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :playlistId")
   suspend fun getNextPosition(playlistId: String): Int

   @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId)")
   suspend fun containsTrack(playlistId: String, trackId: String): Boolean
   ```
2. `MusicRepository.addTrackToPlaylist(playlistId, track, position: Int? = null)`:
   - nếu `position == null` → `getNextPosition(playlistId)`
   - nếu `containsTrack(...)` → return sớm (chống nhân bản khi bấm 2 lần)
3. Bốn call site ở `MainActivity.kt` bỏ tham số `0`.

**Nghiệm thu:** tạo playlist trống → thêm 3 bài khác nhau → playlist hiện đủ 3 bài đúng thứ tự; thêm lại bài đã có → không nhân bản.

### 2.2. B2 — Hết 20 bài là dừng phát
Xem Phase 3 (đây là thay đổi kiến trúc, không phải một dòng patch).

### 2.3. B3 — Phiên nghe không được lưu

**Nguyên nhân:** `onDispose` chạy khi composition bị huỷ, mà `rememberCoroutineScope()` cũng bị cancel cùng lúc ⇒ `savePlaybackSession` gần như không bao giờ hoàn tất.

**Cách sửa:**
1. `MainActivity` giữ `private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`, truyền vào `MainAppHost`.
2. Thêm `LifecycleEventObserver`: khi `ON_STOP` → lưu session (không chỉ lúc chết hẳn).
3. `onDestroy` → lưu lần cuối, rồi `appScope.cancel()` sau khi hoàn tất (dùng `runBlocking` có timeout 500ms cho lần lưu cuối là chấp nhận được, hoặc bỏ qua vì ON_STOP đã lưu).
4. Xoá block `DisposableEffect` cũ.

**Nghiệm thu:** phát 1 bài → vuốt app khỏi recent → mở lại → đúng bài đó, đúng vị trí ±3 giây.

### 2.4. B4 — Restore session không hoạt động

**Cách sửa:** trong `connectAsync` callback, sau `queueManager.initialize(...)`:
```kotlin
val tracks = queueManager.queueState.value
if (tracks.isNotEmpty()) {
    playerConnection.setQueue(
        tracks = tracks,
        startIndex = queueManager.currentIndex.value,
        initialPositionMs = restoredSession?.positionMs ?: 0L,
        autoPlay = false
    )
}
```
Xoá hoàn toàn block tính-toán-rồi-bỏ-đi ở `MainActivity.kt:214-219`.

**Nghiệm thu:** mở app sau khi đã nghe dở → queue có sẵn, đúng bài, đúng vị trí, **không tự phát** (đúng chủ ý `autoPlay=false`).

### 2.5. B5 — Quét nhạc 2 lần

**Cách sửa:** `MusicRepository.loadCandidatePool(forceRefresh: Boolean = false)` + `Mutex` + cache trong RAM.
- Lần gọi đầu trong `connectAsync` là nguồn duy nhất.
- Callback quyền chỉ đọc lại cache đã có (hoặc gọi lại với `forceRefresh = false`).
- Nút "Quét nhạc" trong `LocalMusicScreen` gọi `forceRefresh = true`.

**Nghiệm thu:** log chỉ hiện 1 lần `fetchAudiusCatalog` lúc mở app.

### 2.6. B6 — `syncQueue` lệch index

**Cách sửa:** `PlayerConnection.syncQueue(tracks)` bỏ hẳn việc dùng `player.currentMediaItemIndex`. Thay bằng tìm theo **media id**:
```kotlin
val currentId = player.currentMediaItem?.mediaId
val targetIdx = tracks.indexOfFirst { it.id == currentId }
```
Nếu `targetIdx < 0` → set lại từ đầu; nếu không → `seekTo(targetIdx, currentPos)`.

**Nghiệm thu:** đổi style 3 lần liên tiếp sau khi đã nghe 8 bài → bài đang phát không đổi, bài kế tiếp đổi theo style mới.

---

## 4. Phase 3 — Cầu nối Queue ↔ Player (sửa B2 và chặn cả họ bug tương lai)

Đây là phần đáng làm nhất. Hiện `QueueManager` và `ExoPlayer` là **hai danh sách song song không ai bảo ai** — mọi bug về nhảy bài, lệch index, dừng phát đều mọc ra từ đây.

### 3.1. Tách logic so khớp thành hàm thuần (test được)

Tạo `app/src/main/java/com/onsu/player/QueueReconciler.kt`:

```kotlin
sealed interface QueueOp {
    data object None : QueueOp
    data class FullReset(val startIndex: Int) : QueueOp
    data class TrimHead(val count: Int) : QueueOp
    data class TrimTail(val fromIndex: Int) : QueueOp
    data class AppendTail(val fromIndex: Int, val tracks: List<Track>) : QueueOp
}

object QueueReconciler {
    fun computeOps(
        playerIds: List<String>,
        playerCurrentIndex: Int,
        queue: List<Track>,
        queueCurrentIndex: Int
    ): List<QueueOp>
}
```

Quy tắc:
1. `queue` rỗng → `None`.
2. Track đang phát không còn trong queue → `FullReset`.
3. Đầu danh sách lệch (do head-trim) → `TrimHead(k)` rồi chèn lại phần đầu.
4. Đuôi lệch (do đổi style hoặc nạp thêm) → `TrimTail` + `AppendTail`.
5. Trùng khớp hoàn toàn → `None` (không đụng player, tránh giật âm thanh).

### 3.2. `PlayerConnection` tự đồng bộ

- `PlayerConnection.attachQueue(queueManager, scope)`: collect `queueManager.queueState` + `currentIndex`.
- Với mỗi emission: `QueueReconciler.computeOps(...)` → áp lên player:
  - `TrimHead` → `player.removeMediaItems(0, count)`
  - `TrimTail` → `player.removeMediaItems(from, player.mediaItemCount)`
  - `AppendTail` → `player.addMediaItems(from, tracks.map(MediaItemBuilder::fromTrack))`
  - `FullReset` → `player.setMediaItems(...)`
- **Không** dùng `setMediaItems` cho đường append/trim ⇒ không gián đoạn âm thanh.

### 3.3. Bỏ các chỗ gọi tay
- `MainActivity` bỏ `playerConnection.syncQueue(updatedQueue)` sau `switchTasteStyle` (cầu nối tự lo).
- `skipNext`/`skipPrevious` bỏ việc tự gọi `queueManager.moveToNextTrack()` trước khi `seekToNextMediaItem()` — chỉ còn **một** nguồn sự thật là `onMediaItemTransition`. Hiện tại cả hai cùng tăng index ⇒ nguy cơ nhảy 2 bài.

### 3.4. Test
- `QueueReconcilerTest.kt` (JVM thuần, không cần máy): 6 case — không đổi gì, nạp thêm đuôi, đổi style, head-trim, track hiện tại biến mất, queue rỗng.
- `QueueScoringTest` hiện có giữ nguyên, thêm 1 case playlist position nếu tách được repository ra khỏi `Context` (nếu không, để dành cho instrumented test).

**Nghiệm thu Phase 3:** để app tự phát liên tục 40+ bài qua ranh giới 20 bài đầu mà không dừng, không lặp, không lệch bài đang phát so với UI.

---

## 5. Phase 4 — Bền hoá gu nhạc (tuỳ chọn, sau khi bạn nghiệm thu 1–3)

Hiện `likedTrackIds` / `completedTrackIds` / `skippedTrackIds` / `playHistoryTimestamps` nằm trong RAM (`QueueManager.kt:27-30`) ⇒ **mở lại app là mất sạch**, trong khi like thì vẫn còn trong Room. Gu nhạc vì thế không bao giờ "học" được gì.

### 4.1. Schema
- Entity mới `PlayEventEntity(trackId, type /* LIKE, SKIP, COMPLETE, PLAY */, at)`.
- `AppDatabase` version 1 → 2, thêm `PlayEventDao`.
- `fallbackToDestructiveMigration()` đang bật nên người dùng cũ sẽ mất dữ liệu — **cần quyết định**: viết `Migration(1,2)` thật (khuyến nghị) hay chấp nhận reset.

### 4.2. Ghi & đọc
- `recordSkip` / `recordComplete` / `recordLike` → ghi event (fire-and-forget qua `appScope`).
- `QueueManager.initialize(...)` nhận thêm `history: List<PlayEvent>` để dựng lại 4 set trước khi tính điểm lần đầu.

**Nghiệm thu:** skip 1 bài → tắt app → mở lại → bài đó vẫn bị trừ 0.25 điểm (kiểm tra bằng log hoặc bằng việc nó tụt khỏi 20 bài đầu).

---

## 6. Phase 5 — Tách ViewModel (tuỳ chọn, rủi ro cao)

`MainActivity.kt` hiện 591 dòng, gánh 14 `remember` state + 5 hàm điều phối. Hệ quả thấy được: **xoay màn hình là mất state**, không có chỗ để unit-test luồng UI.

Đề xuất làm **sau cùng**, theo kiểu strangler:
1. Tạo `MusicViewModel` giữ đúng những state đang có (copy nguyên, không "cải tiến" cùng lúc).
2. `MainActivity` chỉ còn `setContent { MixtapeTheme { OnsuApp(viewModel) } }`.
3. Chuyển từng hàm một (`toggleLikeTrack` → `onToggleLike`), chạy lại sau mỗi hàm.
4. Chỉ sau khi ổn định mới cân nhắc thêm `SavedStateHandle`.

Không gộp phase này với 1–4: refactor kiến trúc + sửa bug cùng lúc là công thức để không biết lỗi do đâu.

---

## 7. Thứ tự thực thi & ước lượng

| Phase | Nội dung | File chạm | Rủi ro | Ước lượng |
|---|---|---|---|---|
| 1 | Dọn code chết + dependency + manifest | 4 xoá, 3 sửa | Thấp | ~30 phút |
| 2 | B1, B3, B4, B5, B6 | 5 file | Trung bình | ~1.5 giờ |
| 3 | Cầu nối Queue↔Player + test | 3 file mới, 2 sửa | **Cao** | ~2–3 giờ |
| 4 | Bền hoá gu nhạc (tuỳ chọn) | 5 file | Trung bình | ~1.5 giờ |
| 5 | ViewModel (tuỳ chọn) | 2 file | Cao | ~3 giờ |

**Nguyên tắc:** mỗi phase là một điểm dừng hợp lệ. Xong phase nào tôi báo để bạn build & nghiệm thu phase đó, không dồn cả 5 phase rồi mới đưa bạn một cục thay đổi khổng lồ.

---

## 8. Những gì kế hoạch này **không** làm (nói rõ để khỏi kỳ vọng sai)

- **Không** làm Google Sign-In thật, không gửi email OTP thật. Luồng OTP hiện tại là giả lập client-side (mã hiện ngay trên màn hình). Làm thật cần backend + SMTP/Resend + Google Cloud project — một dự án riêng.
- **Không** làm chức năng tải nhạc offline (nút download hiện là `{}`).
- **Không** thêm tìm kiếm online thật (hiện chỉ lọc 100 track trong RAM).
- **Không** đổi giao diện. Nếu bạn muốn refresh UI, đó là việc sau khi phần lõi đúng đã.
