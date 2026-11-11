package com.onsu.data.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.onsu.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Tài khoản Google đã xác thực thật, lấy từ ID token do Google ký. */
data class GoogleAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

sealed interface GoogleSignInResult {
    data class Success(val account: GoogleAccount) : GoogleSignInResult

    /** Người dùng tự đóng bảng chọn tài khoản — không phải lỗi. */
    data object Cancelled : GoogleSignInResult

    /**
     * @param noCredential true khi máy không có tài khoản Google nào dùng được. Khi đó
     *   màn hình đăng nhập làm nổi bật nút "Dùng chế độ Khách" — nhưng **không tự
     *   chuyển sang Khách**, vì làm vậy sẽ che mất lỗi cấu hình (SHA-1 / package name).
     */
    data class Failed(
        val message: String,
        val noCredential: Boolean = false
    ) : GoogleSignInResult
}

/**
 * Đăng nhập Google thật qua Credential Manager.
 *
 * Khác với bản trước (chỉ import thư viện rồi tự sinh mã OTP trên máy), ở đây
 * Google Play Services mở bảng chọn tài khoản hệ thống và trả về một ID token
 * do Google ký. Email trong token là email thật của tài khoản người dùng chọn.
 *
 * Lưu ý về bảo mật: payload của token được giải mã ở client để lấy email/tên.
 * Chữ ký của token chỉ nên được kiểm tra ở phía server (nếu có backend). Với ứng
 * dụng này, token đến trực tiếp từ Credential Manager trong cùng tiến trình nên
 * không đi qua mạng — rủi ro giả mạo không đặt ra. Nếu sau này cần dùng token để
 * gọi API riêng, hãy xác thực chữ ký ở server trước.
 */
class GoogleSignInClient(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): GoogleSignInResult {
        if (!AuthConfig.isGoogleConfigured) {
            Log.e(TAG, "GOOGLE_WEB_CLIENT_ID chưa được cấu hình (hoặc vẫn là placeholder).")
            return GoogleSignInResult.Failed(
                tr(
                    "GOOGLE_WEB_CLIENT_ID is not configured in .env. See README-AUTH.md for how to create an OAuth Client ID.",
                    "Chưa cấu hình GOOGLE_WEB_CLIENT_ID trong file .env. Xem hướng dẫn tạo OAuth Client ID trong README-AUTH.md."
                )
            )
        }

        val option = GetSignInWithGoogleOption.Builder(AuthConfig.googleWebClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            // CredentialManager bắt buộc gọi từ Activity context trên main thread.
            val response = withContext(Dispatchers.Main) {
                credentialManager.getCredential(context, request)
            }
            val credential = response.credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Log.e(TAG, "Loại credential không mong đợi: ${credential.type}")
                GoogleSignInResult.Failed(
                    tr(
                        "Google returned an unexpected credential type: ${credential.type}",
                        "Google trả về loại thông tin không mong đợi: ${credential.type}"
                    )
                )
            } else {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val account = GoogleIdTokenParser.parse(googleCredential.idToken)
                if (account == null) {
                    Log.e(TAG, "Không giải mã được payload ID token.")
                    GoogleSignInResult.Failed(
                        tr(
                            "Couldn't read the account details from Google's ID token.",
                            "Không đọc được thông tin tài khoản từ ID token của Google."
                        )
                    )
                } else {
                    Log.i(TAG, "Đăng nhập Google thành công: ${account.email}")
                    GoogleSignInResult.Success(account)
                }
            }
        } catch (e: GetCredentialCancellationException) {
            // Người dùng tự đóng bảng chọn: không phải lỗi, không làm gì cả.
            Log.i(TAG, "Người dùng huỷ bảng chọn tài khoản.")
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.e(TAG, "NoCredentialException: máy không có tài khoản Google dùng được.", e)
            GoogleSignInResult.Failed(
                tr(
                    "No Google account found on this device. Add one in Android Settings, or check that the app's SHA-1 is registered on Google Cloud.",
                    "Không tìm thấy tài khoản Google nào trên máy. Hãy thêm tài khoản trong Cài đặt Android, hoặc kiểm tra lại SHA-1 của chữ ký app đã đăng ký trên Google Cloud."
                ),
                noCredential = true
            )
        } catch (e: Exception) {
            // Ghi log đầy đủ (kèm stack trace) rồi mới báo cho người dùng một câu dễ hiểu.
            Log.e(TAG, "Đăng nhập Google thất bại: ${e.javaClass.name}", e)
            GoogleSignInResult.Failed(
                tr(
                    "Google sign-in failed: ${e.message ?: e.javaClass.simpleName}. This usually means the SHA-1 / package name doesn't match the OAuth Client on Google Cloud.",
                    "Đăng nhập Google thất bại: ${e.message ?: e.javaClass.simpleName}. Thường gặp khi SHA-1 / package name chưa khớp với OAuth Client trên Google Cloud."
                )
            )
        }
    }

    /**
     * Xoá trạng thái đăng nhập mà Credential Manager đã lưu cho app này.
     *
     * Không có bước này, sau khi bấm "Đăng xuất" rồi đăng nhập lại, Google vẫn có thể
     * tự chọn lại tài khoản cũ mà không hỏi — người dùng tưởng đã đổi tài khoản nhưng
     * thực tế vẫn là tài khoản trước.
     */
    suspend fun clearCredentialState() {
        runCatching {
            withContext(Dispatchers.Main) {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }.onSuccess {
            Log.i(TAG, "Đã xoá trạng thái đăng nhập của Credential Manager.")
        }.onFailure { e ->
            // Không chặn luồng đăng xuất: dữ liệu người dùng đã bị xoá khỏi Room rồi.
            Log.w(TAG, "Không xoá được credential state: ${e.javaClass.name}", e)
        }
    }

    private companion object {
        const val TAG = "OnsuGoogleSignIn"
    }
}

/**
 * Tách riêng để test được trên JVM: đây là đoạn quyết định mã xác nhận sẽ được gửi
 * tới địa chỉ nào, nên nó cần có test thay vì chỉ được tin bằng mắt.
 */
internal object GoogleIdTokenParser {

    fun parse(idToken: String): GoogleAccount? = runCatching {
        val payload = idToken.split(".").getOrNull(1) ?: return null
        if (payload.isBlank()) return null

        val json = JSONObject(
            String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        )

        val email = json.optString("email").trim()
        if (email.isBlank()) return null

        GoogleAccount(
            id = json.optString("sub").ifBlank { "google_$email" },
            email = email,
            displayName = json.optString("name").ifBlank { email.substringBefore("@") },
            photoUrl = json.optString("picture").takeIf { it.isNotBlank() }
        )
    }.getOrNull()
}
