package com.onsu.data.auth

import android.content.Context
import com.onsu.data.models.UserProfile
import com.onsu.data.repository.MusicRepository

/** Tài khoản Google (đã được Google xác thực) → hồ sơ dùng trong app. */
fun GoogleAccount.toUserProfile(): UserProfile = UserProfile(
    id = id,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    isLoggedIn = true,
    isGuest = false
)

/**
 * Điểm vào duy nhất cho việc xác thực bằng Google.
 *
 * Lớp này gom bốn việc mà trước đây nằm rải rác ở MainActivity:
 *  1. Dựng yêu cầu `GetSignInWithGoogleOption` với Web Client ID và gọi Credential Manager.
 *  2. Đọc email / tên / ảnh / id người dùng từ ID token.
 *  3. Lưu hồ sơ vào Room (nguồn sự thật duy nhất của app — không dùng thêm DataStore,
 *     vì hai nơi lưu song song sẽ lệch nhau sau khi đăng xuất).
 *  4. Đăng xuất: xoá hồ sơ trong Room **và** gọi `clearCredentialState()`.
 *
 * @param context phải là **Activity context**, không phải applicationContext:
 *   Credential Manager cần một Activity để mở bảng chọn tài khoản hệ thống.
 */
class GoogleAuthManager(
    context: Context,
    private val repository: MusicRepository
) {

    private val client = GoogleSignInClient(context)

    /** Mở bảng chọn tài khoản Google. Chưa ghi gì vào Room — chờ xác thực xong mới ghi. */
    suspend fun signIn(): GoogleSignInResult = client.signIn()

    /** Ghi hồ sơ đã xác thực xuống Room. Gọi sau khi đã qua bước OTP (nếu có bật). */
    suspend fun saveProfile(user: UserProfile) {
        repository.saveUserProfile(user)
    }

    /** Đăng xuất: xoá hồ sơ và xoá trạng thái đăng nhập đã lưu trên máy. */
    suspend fun signOut() {
        repository.clearUser()
        client.clearCredentialState()
    }
}
