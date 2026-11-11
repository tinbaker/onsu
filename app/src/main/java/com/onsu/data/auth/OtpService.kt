package com.onsu.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface OtpResult {
    /** Dịch vụ đã nhận yêu cầu và thực sự gửi email. */
    data object Sent : OtpResult

    /** Mã đúng và còn hiệu lực. */
    data object Verified : OtpResult

    data class Failed(val message: String) : OtpResult
}

/**
 * Gửi và kiểm tra mã xác nhận 6 số qua email.
 *
 * Toàn bộ việc sinh mã, đếm thời gian hiệu lực và so khớp nằm ở phía dịch vụ —
 * client không bao giờ được tự sinh mã rồi tự kiểm tra, vì làm vậy thì bất kỳ ai
 * đọc được APK cũng đăng nhập được mà không cần quyền truy cập hộp thư.
 */
interface OtpService {
    suspend fun requestCode(email: String): OtpResult
    suspend fun verifyCode(email: String, code: String): OtpResult
}

internal object OtpHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val json = "application/json; charset=utf-8".toMediaType()

    /** Đọc thông điệp lỗi từ body JSON, ưu tiên các khoá mà Supabase/Functions hay dùng. */
    fun errorMessage(body: String?, fallback: String): String {
        if (body.isNullOrBlank()) return fallback
        return runCatching {
            val obj = JSONObject(body)
            sequenceOf("msg", "error_description", "message", "error")
                .map { obj.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }
}

/**
 * Supabase Auth — gửi mã 6 số thật qua email.
 *
 * Dùng REST trực tiếp bằng OkHttp (đã có sẵn trong dự án) thay vì thêm SDK,
 * để không kéo theo Ktor và một mớ dependency chỉ cho hai endpoint.
 *
 * Điều kiện: email template "Magic Link" trong Supabase phải chứa `{{ .Token }}`
 * thì thư mới có mã 6 số. Nếu template chỉ có `{{ .ConfirmationURL }}`, người dùng
 * sẽ nhận một liên kết thay vì mã.
 */
class SupabaseOtpService(
    private val baseUrl: String,
    private val anonKey: String
) : OtpService {

    /**
     * Supabase có hai kiểu khoá công khai:
     *  - legacy `anon` key: một JWT, bắt đầu bằng "eyJ" → gửi được ở cả hai header.
     *  - khoá mới `sb_publishable_...`: không phải JWT, chỉ dùng ở header `apikey`;
     *    gửi nó vào `Authorization: Bearer` sẽ bị từ chối.
     * Vì vậy chỉ thêm Authorization khi khoá thực sự là JWT.
     */
    private fun Request.Builder.withSupabaseKey(key: String): Request.Builder {
        addHeader("apikey", key)
        if (key.startsWith("eyJ")) addHeader("Authorization", "Bearer $key")
        return this
    }

    override suspend fun requestCode(email: String): OtpResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("create_user", true)
            .toString()

        val request = Request.Builder()
            .url("$baseUrl/auth/v1/otp")
            .withSupabaseKey(anonKey)
            .post(payload.toRequestBody(OtpHttp.json))
            .build()

        runCatching { OtpHttp.client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    val text = it.body?.string()
                    val serviceMessage = OtpHttp.errorMessage(text, "")

                    when {
                        it.isSuccessful -> OtpResult.Sent

                        // Giới hạn thật của Supabase: SMTP mặc định chỉ gửi được tới
                        // email của thành viên trong project.
                        serviceMessage.contains("not authorized", ignoreCase = true) ->
                            OtpResult.Failed(
                                "Supabase từ chối gửi tới địa chỉ này. Gói miễn phí chỉ gửi được " +
                                    "tới email thành viên của project — hãy thêm Gmail này vào Team, " +
                                    "hoặc cấu hình SMTP riêng (xem README-AUTH.md)."
                            )

                        it.code == 429 -> OtpResult.Failed(
                            "Bạn vừa yêu cầu mã. Vui lòng chờ khoảng 60 giây rồi thử lại."
                        )

                        it.code == 422 || it.code == 400 -> OtpResult.Failed(
                            OtpHttp.errorMessage(text, "Email không hợp lệ hoặc bị dịch vụ từ chối.")
                        )

                        else -> OtpResult.Failed(
                            "Không gửi được mã (HTTP ${it.code}): " +
                                OtpHttp.errorMessage(text, "lỗi không xác định")
                        )
                    }
                }
            },
            onFailure = { e ->
                OtpResult.Failed("Không kết nối được tới dịch vụ gửi mã: ${e.message ?: "lỗi mạng"}")
            }
        )
    }

    override suspend fun verifyCode(email: String, code: String): OtpResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("type", "email")
            .put("email", email)
            .put("token", code)
            .toString()

        val request = Request.Builder()
            .url("$baseUrl/auth/v1/verify")
            .withSupabaseKey(anonKey)
            .post(payload.toRequestBody(OtpHttp.json))
            .build()

        runCatching { OtpHttp.client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    when {
                        it.isSuccessful -> OtpResult.Verified
                        it.code == 403 || it.code == 401 || it.code == 400 -> OtpResult.Failed(
                            "Mã không đúng hoặc đã hết hiệu lực. Kiểm tra lại mã trong email mới nhất."
                        )
                        else -> OtpResult.Failed(
                            "Không kiểm tra được mã (HTTP ${it.code}): " +
                                OtpHttp.errorMessage(it.body?.string(), "lỗi không xác định")
                        )
                    }
                }
            },
            onFailure = { e ->
                OtpResult.Failed("Không kết nối được tới dịch vụ xác thực: ${e.message ?: "lỗi mạng"}")
            }
        )
    }
}

/**
 * Cloud Function tự viết (thư mục `firebase/functions`) — dùng khi bắt buộc phải ở
 * trong hệ Firebase.
 *
 * Bản thân Firebase Authentication **không** gửi được mã 6 số: API email của nó chỉ
 * gửi magic link (`sendSignInLinkToEmail`). Muốn có mã 6 số vẫn phải có một hàm
 * server sinh mã, lưu mã và gọi dịch vụ mail. Class này chỉ là client của hàm đó.
 */
class FirebaseFunctionOtpService(private val functionBaseUrl: String) : OtpService {

    override suspend fun requestCode(email: String): OtpResult = post(
        path = "/requestCode",
        body = JSONObject().put("email", email)
    )

    override suspend fun verifyCode(email: String, code: String): OtpResult = post(
        path = "/verifyCode",
        body = JSONObject().put("email", email).put("code", code)
    )

    private suspend fun post(path: String, body: JSONObject): OtpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$functionBaseUrl$path")
            .post(body.toString().toRequestBody(OtpHttp.json))
            .build()

        runCatching { OtpHttp.client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    val text = it.body?.string()
                    if (!it.isSuccessful) {
                        OtpResult.Failed(
                            OtpHttp.errorMessage(text, "Máy chủ trả về HTTP ${it.code}.")
                        )
                    } else {
                        val ok = runCatching { JSONObject(text ?: "{}").optBoolean("ok", true) }
                            .getOrDefault(true)
                        if (!ok) {
                            OtpResult.Failed(
                                OtpHttp.errorMessage(text, "Mã không đúng hoặc đã hết hiệu lực.")
                            )
                        } else if (path == "/verifyCode") {
                            OtpResult.Verified
                        } else {
                            OtpResult.Sent
                        }
                    }
                }
            },
            onFailure = { e ->
                OtpResult.Failed("Không gọi được Cloud Function: ${e.message ?: "lỗi mạng"}")
            }
        )
    }
}

/** Kết quả chọn dịch vụ OTP lúc khởi động. */
data class OtpServiceSetup(
    val service: OtpService?,
    val error: String?
)

object OtpServices {

    /**
     * Trả về service nếu cấu hình đầy đủ, ngược lại trả về lý do để UI hiển thị.
     * Không bao giờ trả về một service "giả" — thà báo thiếu cấu hình còn hơn để
     * người dùng tưởng đã đăng nhập trong khi chưa có email nào được gửi.
     */
    fun create(): OtpServiceSetup = when (AuthConfig.otpProvider) {
        "firebase" -> {
            if (AuthConfig.otpFunctionUrl.isBlank()) {
                OtpServiceSetup(
                    null,
                    "OTP_PROVIDER=firebase nhưng chưa có OTP_FUNCTION_URL trong .env"
                )
            } else {
                OtpServiceSetup(FirebaseFunctionOtpService(AuthConfig.otpFunctionUrl), null)
            }
        }

        else -> {
            if (AuthConfig.supabaseUrl.isBlank() || AuthConfig.supabaseAnonKey.isBlank()) {
                OtpServiceSetup(
                    null,
                    "Chưa cấu hình SUPABASE_URL / SUPABASE_ANON_KEY trong .env"
                )
            } else {
                OtpServiceSetup(
                    SupabaseOtpService(AuthConfig.supabaseUrl, AuthConfig.supabaseAnonKey),
                    null
                )
            }
        }
    }
}
