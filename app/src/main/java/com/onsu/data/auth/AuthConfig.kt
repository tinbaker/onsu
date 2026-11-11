package com.onsu.data.auth

import com.onsu.BuildConfig

/**
 * Cấu hình xác thực, đọc từ `.env` qua Secrets Gradle Plugin.
 *
 * Không có giá trị nào được hard-code trong mã nguồn: Client ID và khoá dịch vụ
 * đều nằm ngoài repo. Khi thiếu cấu hình, ứng dụng không được phép "giả lập thành
 * công" — nó phải báo lỗi rõ ràng, vì đó chính là thứ đã khiến bản trước trông
 * như đang chạy trong khi thực tế chẳng gửi email nào.
 */
object AuthConfig {

    /**
     * Giá trị placeholder trong `.env.example` bắt đầu bằng các tiền tố này.
     * Coi như chưa cấu hình để không gửi request rác tới dịch vụ.
     */
    private fun sanitize(raw: String): String {
        val trimmed = raw.trim()
        val isPlaceholder = trimmed.isEmpty() ||
            trimmed.startsWith("YOUR_") ||
            trimmed.startsWith("PASTE_") ||
            trimmed.equals("null", ignoreCase = true)
        return if (isPlaceholder) "" else trimmed
    }

    /** OAuth Client ID loại "Web application" — bắt buộc để Credential Manager trả về ID token. */
    val googleWebClientId: String
        get() = sanitize(BuildConfig.GOOGLE_WEB_CLIENT_ID)

    val isGoogleConfigured: Boolean
        get() = googleWebClientId.endsWith(".apps.googleusercontent.com")

    /** "supabase" hoặc "firebase" (Cloud Function tự viết). */
    val otpProvider: String
        get() = sanitize(BuildConfig.OTP_PROVIDER).lowercase().ifEmpty { "supabase" }

    val supabaseUrl: String
        get() = sanitize(BuildConfig.SUPABASE_URL).trimEnd('/')

    val supabaseAnonKey: String
        get() = sanitize(BuildConfig.SUPABASE_ANON_KEY)

    /** Base URL của Cloud Function gửi OTP (chỉ dùng khi otpProvider = "firebase"). */
    val otpFunctionUrl: String
        get() = sanitize(BuildConfig.OTP_FUNCTION_URL).trimEnd('/')

    /**
     * Có bắt buộc nhập thêm mã xác nhận qua email sau khi đăng nhập Google hay không.
     *
     * Mặc định **không**. Google đã xác thực chủ tài khoản và ký ID token, nên bước
     * gửi thêm mã về cùng hộp thư đó không thêm được bảo vệ nào — nó chỉ là một lớp
     * phụ thuộc vào dịch vụ gửi mail, kèm giới hạn tần suất và rủi ro thư vào Spam.
     * Bật lại bằng `AUTH_REQUIRE_OTP=true` trong `.env` nếu muốn giữ luồng hai bước.
     */
    val requireOtp: Boolean
        get() = sanitize(BuildConfig.AUTH_REQUIRE_OTP).equals("true", ignoreCase = true)
}
