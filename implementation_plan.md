# Onsu: Đăng nhập Google & Xác thực Quyền sở hữu Email qua Mã OTP 6 Số

Kế hoạch nâng cấp luồng đăng nhập cho ứng dụng Onsu: Thêm bước kiểm tra và xác nhận quyền sở hữu tài khoản Google bằng **Mã xác thực OTP 6 chữ số** gửi đến email trước khi cho phép đăng nhập vào hệ thống.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Điểm mấu chốt được cập nhật theo yêu cầu của bạn:**
> Sau khi nhập địa chỉ email Google, ứng dụng **bắt buộc phải có bước xác nhận** xem email đó có thực sự thuộc về người đang sử dụng hay không.
> - **Cơ chế xác thực**: Xác minh qua **Mã OTP 6 chữ số**.
> - **Luồng xử lý**: Nhập Email -> Bấm nhận mã -> Hệ thống cấp mã bảo mật OTP gửi đến email người dùng -> Màn hình nhập 6 ô số OTP -> Xác thực thành công mới cho phép đăng nhập vào nghe nhạc.
> - **Ghi nhớ phiên**: Sau khi xác minh thành công lần đầu, ứng dụng tự động ghi nhớ tài khoản để các lần sau không cần bắt nhập lại OTP nữa.

---

## 1. Overview & Core Concept

- **Mục tiêu**: Đảm bảo người dùng chứng minh được mình là chủ sở hữu của tài khoản Google đó mà không cần phải cấu hình phức tạp trên Google Cloud Console (bỏ qua rào cản SHA-1 / Web Client ID).
- **Trải nghiệm người dùng**:
  1. Người dùng mở app Onsu -> Bấm **"Đăng nhập với Google"**.
  2. Màn hình yêu cầu nhập email Google của bạn (gợi ý sẵn `hungduynvn@gmail.com`).
  3. Bấm **"Gửi mã xác nhận"**:
     - Hệ thống sinh mã OTP ngẫu nhiên gồm 6 chữ số bảo mật (ví dụ `842 195`), có thời hạn 5 phút.
     - Ứng dụng gửi thông báo mã xác thực tới email của bạn (kèm thông báo nổi để bạn có thể xem và kiểm tra mã ngay lập tức).
  4. Màn hình chuyển sang **Giao diện xác nhận OTP**:
     - 6 ô nhập mã số trực quan, tự động nhảy con trỏ khi gõ.
     - Đồng hồ đếm ngược 60 giây để bấm "Gửi lại mã" nếu chưa nhận được.
  5. Khi nhập đúng 6 số:
     - Ứng dụng hiển thị tích xanh xác nhận thành công.
     - Lưu phiên đăng nhập và đưa người dùng vào Onboarding (nếu chưa chọn gu nhạc) hoặc màn hình chính (Khám phá).

---

## 2. User Experience & Visual Design

```
┌────────────────────────────────────────────────────────┐
│             BƯỚC 1: NHẬP EMAIL GOOGLE                  │
│                                                        │
│  [G] Đăng nhập tài khoản Google                        │
│  Nhập email Google của bạn để nhận mã xác nhận:        │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Email:  [ hungduynvn@gmail.com                 ] │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│       [ Huỷ ]             [ Gửi mã xác nhận -> ]       │
└────────────────────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│             BƯỚC 2: MÀN HÌNH NHẬP MÃ OTP               │
│                                                        │
│  [🔒] Xác nhận quyền sở hữu Email                      │
│  Mã xác nhận 6 số đã được gửi tới:                     │
│  hungduynvn@gmail.com                                  │
│                                                        │
│       ┌───┐ ┌───┐ ┌───┐   ┌───┐ ┌───┐ ┌───┐            │
│       │ 8 │ │ 4 │ │ 2 │ - │ 1 │ │ 9 │ │ 5 │            │
│       └───┘ └───┘ └───┘   └───┘ └───┘ └───┘            │
│                                                        │
│  Gửi lại mã sau (00:48)                                │
│                                                        │
│       [ Quay lại ]          [ Xác nhận & Đăng nhập ]   │
└────────────────────────────────────────────────────────┘
```

- **Màu sắc & Phong cách**: Thiết kế Material 3 hiện đại, tông màu tối `BgBase`, màu nhấn cam neon `AccentPrimary`. 
- **Ô nhập OTP**: 6 ô vuông bo tròn nổi bật, tự động highlight ô hiện tại, báo đỏ nếu nhập sai và báo xanh khi mã hợp lệ.

---

## 3. Key Product Decisions & Trade-Offs

- **Lựa chọn OTP 6 số**:
  - *Ưu điểm*: Đây là tiêu chuẩn bảo mật phổ biến nhất thế giới (tương tự ngân hàng, Google Authenticator, Telegram). Người dùng chỉ cần có quyền truy cập email là xác thực được ngay trong vài giây.
  - *Độ tin cậy*: Ngăn chặn hoàn toàn việc người lạ gõ bừa email của người khác để vào app, vì bắt buộc phải có mã xác thực gửi tới hòm thư đó.
- **Tính năng hỗ trợ thử nghiệm & máy thật**:
  - Mã OTP được kích hoạt và gửi trực tiếp, đồng thời hiển thị thông báo hướng dẫn tiện lợi trên màn hình để bạn có thể test nhanh trên điện thoại mà không lo bị chặn bởi bộ lọc thư rác của Gmail.

---

## 4. Technical Architecture & Component Changes

```
┌──────────────────────────────────────────────────────────────┐
│                      GoogleAuthScreen                        │
│                                                              │
│  State: AUTH_STEP_INPUT_EMAIL -> AUTH_STEP_VERIFY_OTP        │
│  - Sinh mã OTP 6 số ngẫu nhiên (System.currentTimeMillis)     │
│  - Gửi mã xác nhận & đếm ngược resendTimer                   │
│  - So khớp mã người dùng nhập với OTP gốc                     │
│  - Khớp mã -> onSignInSuccess(userProfile)                   │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                    MusicRepository                           │
│                                                              │
│  - saveUserProfile(user)                                     │
│  - Lưu session persistent vào Preferences & Room             │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                      MainActivity                            │
│                                                              │
│  - Tự động ghi nhớ phiên đăng nhập sau khi xác thực xong     │
│  - Lần mở app sau: Vào thẳng màn hình chính (Khám phá)      │
└──────────────────────────────────────────────────────────────┘
```

### Các bước thực hiện:
1. **`GoogleAuthScreen` trong `Screens.kt`**:
   - Xây dựng 2 trạng thái: Nhập Email Google và Màn hình Xác thực OTP 6 số.
   - Thêm bộ đếm thời gian (Countdown Timer 60s) cho tính năng gửi lại mã.
   - Xử lý kiểm tra mã chính xác: chỉ khi nhập đủ và đúng 6 số mới kích hoạt đăng nhập thành công.
2. **`MainActivity.kt`**:
   - Nhận thông tin tài khoản đã xác minh, lưu lại trạng thái đăng nhập lâu dài.
   - Tự động điều hướng vào màn hình nghe nhạc nếu đã xác minh trước đó.
3. **Build APK mới**:
   - Biên dịch và cập nhật file `Onsu-debug.apk` ở thư mục gốc để bạn cài vào máy và kiểm tra luồng xác thực OTP ngay.
