# Cấu hình đăng nhập thật bằng tài khoản Google

Có hai mức, chọn theo nhu cầu:

| Mức | Thời gian | Cần gì | Phù hợp |
|---|---|---|---|
| **Chỉ Google** (mặc định) | ~10 phút | Google Cloud OAuth | Demo nhiều người, không giới hạn số tài khoản |
| Google + mã 6 số qua email | ~25 phút | thêm Supabase (+ SMTP riêng) | Khi môn học yêu cầu 2 lớp xác thực |

Kết quả cuối: bấm "Đăng nhập với Google" → hiện bảng chọn tài khoản của Android →
chọn tài khoản → **vào app ngay** (hoặc nhập thêm mã 6 số, nếu bật).

---

## Vì sao mặc định bỏ bước mã xác nhận

Google đã kiểm tra chủ tài khoản và ký ID token. Gửi thêm mã về chính hộp thư đó không
chứng minh thêm được gì — nó chỉ thêm một dịch vụ có thể hỏng, có giới hạn tần suất, và
có nguy cơ thư vào Spam đúng lúc bạn đang demo. Với 5 người đăng nhập, chế độ chỉ Google
không gặp bất kỳ giới hạn nào.

Bật lại bằng một dòng trong `.env`:

```env
AUTH_REQUIRE_OTP=true
```

---

# Phần A — Bắt buộc: Google Sign-In (~10 phút)

## A1. Tạo project trên Google Cloud

1. Vào <https://console.cloud.google.com/> → **Select a project → New project**.
   Đặt tên `Onsu` → **Create**, chờ vài giây rồi chọn project vừa tạo.
2. Menu trái → **APIs & Services → OAuth consent screen**
   (giao diện mới tên là **Google Auth Platform**):
   - Bấm **Get started**.
   - **App name**: `Onsu`
   - **User support email**: Gmail của bạn
   - **Audience**: chọn **External**
   - **Contact information**: Gmail của bạn
   - Đồng ý điều khoản → **Create**.

## A2. Cho phép ≥5 người đăng nhập

Đây là chỗ quyết định buổi demo thành công hay không. Khi app còn ở trạng thái
**Testing**, Google **chỉ cho những tài khoản nằm trong danh sách Test users** đăng nhập
(tối đa 100 người). Chọn một trong hai cách:

| Cách | Làm gì | Phù hợp khi |
|---|---|---|
| Thêm test user | **Google Auth Platform → Audience → Test users → Add users**, nhập Gmail từng người | Biết trước danh sách người demo |
| **Publish app** | **Google Auth Platform → Audience → Publish app** | Demo cho người chưa biết trước |

Với quyền `openid email profile` (loại không nhạy cảm), Publish thường được duyệt tự
động, không phải chờ Google xác minh. Sau khi Publish, **bất kỳ tài khoản Google nào**
cũng đăng nhập được.

> Triệu chứng nếu bỏ sót bước này: người thứ 3 bấm đăng nhập và thấy
> *"Access blocked: Onsu has not completed the Google verification process"*.

## A3. Tạo OAuth Client ID loại **Web application**

**APIs & Services → Credentials → Create credentials → OAuth client ID → Web application.**

- **Name**: `Onsu Web`
- Không cần điền Authorized redirect URI.

Bấm **Create**, copy **Client ID** (dạng `1234567890-abcxyz.apps.googleusercontent.com`).
Đây là giá trị cho `GOOGLE_WEB_CLIENT_ID`.

> Vì sao là "Web" mà không phải "Android"? Vì Credential Manager xin **ID token** cho
> `serverClientId`, và Google chỉ cấp ID token cho client loại Web. Client Android ở
> bước sau chỉ để Google biết app nào được phép hỏi.

## A4. Tạo OAuth Client ID loại **Android**

Tạo thêm một client, chọn **Android**:

| Trường | Giá trị |
|---|---|
| Package name | `com.onsu.music` |
| SHA-1 | `21:99:BA:E4:B3:CC:B2:30:55:15:AF:10:6B:B8:20:E8:A1:66:59:65` |

SHA-256 (chỉ điền nếu Google yêu cầu):

```
79:EB:A4:55:F1:E0:7E:AB:6B:73:19:64:54:65:2C:AB:78:9D:BA:D9:70:8F:D8:9B:EF:5F:A6:3D:79:6A:35:94
```

> `debug.keystore` được commit cùng repo để mọi máy clone có cùng SHA-1 (khoá debug,
> mật khẩu công khai `android`, **không** dùng để ký release). Nếu build release bằng
> keystore khác, phải thêm client Android thứ hai với SHA-1 tương ứng, nếu không bản
> release sẽ không đăng nhập được.

## A5. Điền `.env` và build

> Repo này là **private**: `.env` **không có trong repo** (đã nằm trong `.gitignore`).
> Mỗi người clone phải tự tạo file, dùng Client ID của project Google Cloud của mình
> hoặc xin chủ repo gửi riêng. Thiếu `.env` thì app vẫn build và chạy ở chế độ Khách,
> chỉ riêng nút đăng nhập Google bị vô hiệu.

```bash
cp .env.example .env
```

Mở `.env`, điền Client ID:

```env
GOOGLE_WEB_CLIENT_ID=1234567890-abcxyz.apps.googleusercontent.com
AUTH_REQUIRE_OTP=false
```

Build bằng Gradle wrapper đi kèm repo (Android Studio bản mới hỗ trợ AGP 9.1.1,
JDK 23, SDK Platform 36.1):

```bash
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Cần `JAVA_HOME` trỏ tới JDK 23 và `ANDROID_HOME` (hoặc `local.properties`) trỏ tới
Android SDK trên máy. Không cần cài Gradle riêng.

Kiểm tra khoá đã vào APK chưa:

```bash
grep -E "GOOGLE_WEB_CLIENT_ID|AUTH_REQUIRE_OTP" \
  app/build/generated/source/buildConfig/debug/com/onsu/BuildConfig.java
```

Nếu vẫn thấy `YOUR_...` nghĩa là `.env` chưa được đọc — kiểm tra tên file có đúng là
`.env` (không phải `.env.txt`) và nằm ở thư mục gốc dự án.

---

# Phần B — Tuỳ chọn: mã 6 số qua email (Supabase)

Chỉ làm phần này khi đặt `AUTH_REQUIRE_OTP=true`.

## ⚠️ Hai giới hạn của Supabase free tier

Tôi đã kiểm tra tài liệu chính thức. SMTP mặc định của Supabase có hai giới hạn **sẽ
chặn bạn ngay khi test**:

| Giới hạn | Hệ quả |
|---|---|
| Chỉ gửi được **2 email/giờ** | Test được 2 lần, sau đó chờ hết giờ. Sửa template rồi test lại cũng tính là 1 lần. |
| Chỉ gửi tới **email thành viên của project** | Gmail của bạn thì được; Gmail của bạn bè báo `Email address not authorized`. |

Cách xử lý: **B5 — cấu hình SMTP riêng** (Brevo, miễn phí 300 thư/ngày). Nếu chỉ test
một mình với chính Gmail đã tạo project, có thể bỏ qua B5 lúc đầu.

## B1. Tạo project Supabase

1. <https://supabase.com> → **Sign in with GitHub** (hoặc email).
2. **New project**: tên `onsu`, mật khẩu database bấm *Generate* rồi lưu lại,
   **Region**: `Southeast Asia (Singapore)`.
3. **Create new project**, chờ ~2 phút.

## B2. Bật đăng nhập email, đặt độ dài mã

1. **Authentication → Sign In / Providers** → mục **Email**: bật **Enable Email provider**
   (mặc định đã bật), giữ **Confirm email**.
2. Kéo xuống **Email OTP**:
   - **Email OTP Length**: `6` ← app đang chờ đúng 6 chữ số.
   - **Email OTP Expiration**: `600` giây.
3. **Save**.

## B3. Sửa template để thư có mã 6 số

Bước **dễ bỏ sót nhất**. Template mặc định chỉ có một liên kết; không sửa thì người dùng
nhận thư không có mã nào để nhập.

1. **Authentication → Email Templates → tab Magic Link**.
2. **Subject heading**: `Mã xác nhận Onsu`
3. Thay **Message body** bằng:

```html
<div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:480px;margin:0 auto;padding:24px">
  <h2 style="margin:0 0 8px">Mã xác nhận của bạn</h2>
  <p style="color:#555;margin:0 0 20px">Nhập mã dưới đây vào ứng dụng Onsu để hoàn tất đăng nhập.</p>

  <div style="font-size:34px;font-weight:700;letter-spacing:10px;padding:16px 0;text-align:center;background:#f4f4f5;border-radius:10px">
    {{ .Token }}
  </div>

  <p style="color:#777;font-size:13px;margin-top:20px">
    Mã có hiệu lực trong vài phút. Nếu bạn không yêu cầu mã này, hãy bỏ qua email.
  </p>
</div>
```

4. **Save changes**.

> `{{ .Token }}` là biến Supabase thay bằng mã thật. Nếu template chỉ có
> `{{ .ConfirmationURL }}`, đó là liên kết — app không có mã để so sánh và sẽ luôn báo
> "Mã không đúng".

## B4. Lấy Project URL và khoá API

**Project Settings → API Keys**:

- **Project URL**: `https://abcdefghijklmno.supabase.co`
- Khoá công khai, tuỳ thời điểm tạo project:
  - Project mới: **Publishable key** (`sb_publishable_...`)
  - Project cũ: tab **Legacy API keys** → **anon public** (`eyJhbGciOi...`)

Cả hai dùng được — app tự nhận biết. **Tuyệt đối không dùng `service_role` / `secret
key`**: khoá đó bỏ qua mọi phân quyền.

## B5. SMTP riêng (khuyến nghị)

**Brevo**, miễn phí 300 thư/ngày, không cần tên miền riêng:

1. Đăng ký <https://www.brevo.com> → xác nhận email.
2. **Senders & IP → Senders → Add a sender** → điền Gmail của bạn → xác nhận qua thư.
3. **SMTP & API → tab SMTP** → ghi lại: host `smtp-relay.brevo.com`, port `587`,
   login là email Brevo, password là **SMTP key** (bấm *Generate a new SMTP key*, chỉ
   hiện một lần).
4. Supabase → **Authentication → SMTP Settings** → bật **Enable Custom SMTP**, điền
   host/port/user/password ở trên, **Sender email** là Gmail đã xác nhận, **Sender
   name**: `Onsu`.
5. **Save**. Từ đây gửi được tới mọi địa chỉ, 30 thư/giờ (chỉnh ở
   **Authentication → Rate Limits**).

## B6. `.env` cho chế độ có OTP

```env
GOOGLE_WEB_CLIENT_ID=1234567890-abcxyz.apps.googleusercontent.com
AUTH_REQUIRE_OTP=true
OTP_PROVIDER=supabase
SUPABASE_URL=https://abcdefghijklmno.supabase.co
SUPABASE_ANON_KEY=sb_publishable_xxxxxxxxxxxx
```

Build lại như A5.

---

# Phần C — Những gì đã có trong code

Phần này **không phải việc bạn phải làm**: code đã viết xong. Đây là bản đồ để biết
tìm gì ở đâu khi bảo vệ đồ án.

| Bước | Yêu cầu | Nằm ở đâu |
|---|---|---|
| C1 | Thư viện Credential Manager | `gradle/libs.versions.toml` → `credentials = 1.6.0`, `googleid = 1.1.1` |
| C2 | Không hard-code Client ID | `.env` → Secrets Gradle Plugin → `BuildConfig` → `AuthConfig.googleWebClientId` |
| C3 | `GoogleAuthManager` | `app/src/main/java/com/onsu/data/auth/GoogleAuthManager.kt` |
| C4 | Xử lý lỗi + luồng màn hình | `GoogleSignInClient.kt` (lỗi) và `MainActivity.kt` (điều hướng) |
| C5 | Màn hình đăng nhập | `GoogleAuthScreen` trong `ui/screens/Screens.kt` |
| C6 | Cài đặt: tên, email, ảnh, đăng xuất | `SettingsScreen` trong cùng file |

Chi tiết từng điểm:

- **C3 — bốn việc gom vào một lớp.** `GoogleAuthManager` dựng `GetSignInWithGoogleOption`
  với Web Client ID, gọi `CredentialManager.getCredential` từ Activity, đọc
  email/tên/ảnh/id từ ID token, ghi hồ sơ vào **Room** (không dùng thêm DataStore — hai
  nơi lưu song song sẽ lệch nhau sau khi đăng xuất), và đăng xuất bằng `clearUser()` +
  `clearCredentialState()`.
- **C4 — không bao giờ im lặng chuyển sang Khách.** Huỷ bảng chọn → không làm gì.
  `NoCredentialException` → ghi log kèm stack trace, báo rõ, và **làm nổi bật** nút
  "Dùng chế độ Khách" nhưng vẫn để người dùng tự bấm. Lỗi khác → ghi log đầy đủ và hiện
  nguyên nhân, vì tự chuyển sang Khách sẽ che mất lỗi SHA-1/package name.
- **C4 — mở app.** Có hồ sơ đã đăng nhập → vào thẳng app (chưa chọn gu thì vào
  onboarding). Chưa có hồ sơ → màn hình đăng nhập.
- **C6 — ảnh đại diện** lấy từ trường `picture` của ID token, tải bằng Coil; không có ảnh
  thì hiện chữ cái đầu của tên.

Hai chỗ khác với bản mô tả ban đầu, có lý do:

1. **Client ID nằm trong `.env`, không phải `local.properties`.** Cả hai đều đã nằm
   trong `.gitignore` nên đều không lên GitHub. `.env` được chọn vì toàn bộ Phần B
   (Supabase) cũng đọc từ đó — một nguồn cấu hình duy nhất, không phải nhớ "giá trị này
   ở file nào".
2. **`googleid` giữ ở 1.1.1, không lên 1.2.1.** Bản 1.2.x build bằng Kotlin 2.4, còn dự
   án dùng Kotlin 2.2.10, nên trình biên dịch báo
   `incompatible version of Kotlin ... metadata is 2.4.0`. Muốn lên 1.2.1 thì phải nâng
   Kotlin toàn dự án trước — không đáng đánh đổi cho một bản demo. `credentials 1.6.0`
   thì dùng được bình thường.

---

# Kiểm thử

**Chế độ chỉ Google** (`AUTH_REQUIRE_OTP=false`):

| # | Làm gì | Kết quả đúng |
|---|---|---|
| 1 | Mở app → **Tiếp tục với Google** | Bảng chọn tài khoản Android hiện ra |
| 2 | Chọn tài khoản | Vào thẳng onboarding, không hỏi mã |
| 3 | Mở **Cài đặt** | Hiện đúng tên, Gmail và ảnh đại diện vừa chọn |
| 4 | Đăng xuất → đăng nhập tài khoản khác | Vào được với tài khoản thứ hai, Cài đặt hiện **tài khoản mới** |
| 5 | Huỷ bảng chọn tài khoản (bấm ra ngoài) | Ở lại màn hình đăng nhập, app không crash |
| 6 | Tắt hẳn app rồi mở lại | Vẫn còn đăng nhập, vào thẳng app |

Làm bước 4 với **đủ 5 tài khoản** trước buổi demo, đừng để nước tới chân mới nhảy.

**Chế độ có OTP** (`AUTH_REQUIRE_OTP=true`): thêm hai bước — hộp thoại hiện đúng email
và "Đang gửi email…"; mở Gmail (cả **Spam**) thấy thư có 6 số; nhập sai mã thì báo
"Mã không đúng hoặc đã hết hiệu lực".

---

# Lỗi thường gặp

| Thông báo trong app | Nguyên nhân | Cách sửa |
|---|---|---|
| `Chưa cấu hình GOOGLE_WEB_CLIENT_ID` | `.env` trống hoặc chưa build lại | Điền `.env` → build lại |
| `Không tìm thấy tài khoản Google nào trên máy` | Chưa tạo OAuth client **Android**, hoặc SHA-1 sai | Làm A4 |
| `Đăng nhập Google thất bại: ... mã 10` | SHA-1 không khớp chữ ký APK | Kiểm tra SHA-1; build release phải thêm SHA-1 của keystore release |
| *Access blocked: ... has not completed verification* | App còn ở Testing, Gmail chưa nằm trong Test users | Làm A2 |
| `Supabase từ chối gửi tới địa chỉ này` | SMTP mặc định, email không thuộc Team | Làm B5 |
| `Không gửi được mã (HTTP 429)` | Quá 2 thư/giờ, hoặc gửi lại trong 60 giây | Chờ, hoặc làm B5 |
| Thư không có mã, chỉ có nút bấm | Template thiếu `{{ .Token }}` | Làm lại B3 |
| `Mã không đúng hoặc đã hết hiệu lực` | Nhập mã của thư cũ | Dùng mã trong thư **mới nhất** |

---

# Ghi chú bảo mật

- Chế độ chỉ Google: danh tính do Google xác thực, ID token được Google ký. App không
  lưu mật khẩu và không có quyền truy cập Gmail.
- Chế độ có OTP: mã do **Supabase** sinh và **Supabase** kiểm tra; app chỉ gửi email +
  mã lên và nhận kết quả. Bản trước sinh mã ngay trong máy rồi tự so sánh — ai đọc được
  APK cũng đăng nhập được mà không cần hộp thư. Phần đó đã bị xoá hoàn toàn.
- ID token được giải mã ở client để lấy email/tên. Chữ ký token chỉ nên kiểm tra ở
  server; app chưa có backend nên chưa kiểm tra. Nếu sau này dùng token để gọi API
  riêng, hãy xác thực chữ ký trước.
- `.env` nằm trong `.gitignore` và **không được đưa lên GitHub**. Client ID là định
  danh công khai về mặt kỹ thuật nhưng vẫn gắn với project Google Cloud và hạn ngạch
  của chủ repo: mỗi cộng tác viên nên tự tạo OAuth client hoặc xin giá trị qua kênh
  riêng. Nếu lỡ push, phải thu hồi/tạo lại Client ID — xoá commit không đủ.
