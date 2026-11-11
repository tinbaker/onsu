package com.onsu

import com.onsu.data.auth.GoogleIdTokenParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * Kiểm tra phần đọc ID token của Google.
 *
 * Đây là đoạn quyết định mã xác nhận sẽ được gửi tới địa chỉ nào. Nếu nó đọc sai
 * trường `email`, người dùng sẽ nhận mã ở một hộp thư khác — lỗi rất khó thấy khi
 * test bằng tay vì màn hình vẫn hiển thị một địa chỉ trông hợp lệ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleIdTokenParserTest {

    private fun jwtWithPayload(payload: String): String {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "eyJhbGciOiJSUzI1NiIsImtpZCI6ImZha2UifQ.$encoded.fake-signature"
    }

    @Test
    fun `doc dung email ten va anh tu payload`() {
        val token = jwtWithPayload(
            """{"sub":"109876543210","email":"duyhung@gmail.com","name":"Duy Hùng","picture":"https://lh3.googleusercontent.com/a/abc"}"""
        )

        val account = GoogleIdTokenParser.parse(token)

        assertEquals("109876543210", account?.id)
        assertEquals("duyhung@gmail.com", account?.email)
        assertEquals("Duy Hùng", account?.displayName)
        assertEquals("https://lh3.googleusercontent.com/a/abc", account?.photoUrl)
    }

    @Test
    fun `thieu ten thi lay phan truoc dau a lam ten hien thi`() {
        val token = jwtWithPayload("""{"sub":"1","email":"someone@gmail.com"}""")

        val account = GoogleIdTokenParser.parse(token)

        assertEquals("someone", account?.displayName)
        assertNull(account?.photoUrl)
    }

    @Test
    fun `payload khong co email thi tra ve null`() {
        val token = jwtWithPayload("""{"sub":"1","name":"Không có email"}""")

        assertNull(GoogleIdTokenParser.parse(token))
    }

    @Test
    fun `token rac thi tra ve null chu khong nem loi`() {
        assertNull(GoogleIdTokenParser.parse("khong-phai-jwt"))
        assertNull(GoogleIdTokenParser.parse(""))
        assertNull(GoogleIdTokenParser.parse("a.!!!khong-phai-base64!!!.c"))
    }

    @Test
    fun `email co khoang trang thua duoc cat gon`() {
        val token = jwtWithPayload("""{"sub":"1","email":"  spaced@gmail.com  "}""")

        assertEquals("spaced@gmail.com", GoogleIdTokenParser.parse(token)?.email)
    }
}
