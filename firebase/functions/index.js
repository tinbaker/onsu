/**
 * Onsu — Cloud Function sinh / gửi / kiểm tra mã xác nhận 6 số.
 *
 * Vì sao phải có hàm này: Firebase Authentication không có API gửi mã 6 số qua
 * email, chỉ có magic link (`sendSignInLinkToEmail`). Muốn có mã thì phải tự sinh,
 * tự lưu và tự gửi — và phải làm ở server, vì nếu client tự sinh mã thì bất kỳ ai
 * đọc được APK cũng đăng nhập được mà không cần hộp thư.
 *
 * Điểm cuối:
 *   POST /requestCode  { email }          -> gửi mã, trả { ok: true }
 *   POST /verifyCode   { email, code }    -> trả { ok: true } hoặc 403 { ok: false, message }
 *
 * Biến môi trường (đặt bằng `firebase functions:secrets:set`):
 *   RESEND_API_KEY  khoá API của resend.com
 *   MAIL_FROM       địa chỉ gửi, ví dụ "Onsu <no-reply@tenmien.com>"
 *   OTP_PEPPER      (tuỳ chọn) chuỗi bí mật trộn vào hash của mã
 */
const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.firestore();

const OTP_TTL_MS = 10 * 60 * 1000; // mã sống 10 phút
const RESEND_COOLDOWN_MS = 60 * 1000; // 60 giây giữa hai lần gửi
const MAX_ATTEMPTS = 5;

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function hashCode(email, code) {
  const pepper = process.env.OTP_PEPPER || "onsu-default-pepper";
  return crypto.createHmac("sha256", pepper).update(`${email}:${code}`).digest("hex");
}

function randomCode() {
  // 100000..999999, không dùng Math.random để tránh đoán được chuỗi
  return String(crypto.randomInt(100000, 1000000));
}

async function sendMail(to, code) {
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${process.env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: process.env.MAIL_FROM,
      to: [to],
      subject: "Mã xác nhận Onsu",
      html: `
        <div style="font-family:sans-serif;line-height:1.6">
          <h2>Mã xác nhận của bạn</h2>
          <p style="font-size:32px;letter-spacing:8px;font-weight:bold;margin:16px 0">${code}</p>
          <p>Mã có hiệu lực trong 10 phút. Nếu bạn không yêu cầu mã này, hãy bỏ qua email.</p>
        </div>`,
    }),
  });

  if (!response.ok) {
    throw new Error(`Resend trả về ${response.status}: ${await response.text()}`);
  }
}

exports.onsuAuth = onRequest({ region: "asia-southeast1", cors: false }, async (req, res) => {
  const route = req.path.replace(/\/+$/, "");

  if (req.method !== "POST") {
    res.status(405).json({ ok: false, message: "Chỉ hỗ trợ POST." });
    return;
  }

  const body = req.body || {};
  const email = String(body.email || "").trim().toLowerCase();

  if (!EMAIL_RE.test(email)) {
    res.status(400).json({ ok: false, message: "Email không hợp lệ." });
    return;
  }

  const ref = db.collection("otpCodes").doc(email);

  if (route === "/requestCode") {
    const snapshot = await ref.get();
    const now = Date.now();

    if (snapshot.exists) {
      const lastSentAt = snapshot.get("lastSentAt") || 0;
      if (now - lastSentAt < RESEND_COOLDOWN_MS) {
        const waitSec = Math.ceil((RESEND_COOLDOWN_MS - (now - lastSentAt)) / 1000);
        res.status(429).json({ ok: false, message: `Vui lòng chờ ${waitSec} giây rồi thử lại.` });
        return;
      }
    }

    const code = randomCode();

    try {
      await sendMail(email, code);
    } catch (error) {
      console.error("Gửi mail thất bại:", error);
      res.status(502).json({ ok: false, message: "Không gửi được email. Kiểm tra cấu hình Resend." });
      return;
    }

    await ref.set({
      codeHash: hashCode(email, code),
      expiresAt: now + OTP_TTL_MS,
      lastSentAt: now,
      attempts: 0,
    });

    res.status(200).json({ ok: true });
    return;
  }

  if (route === "/verifyCode") {
    const code = String(body.code || "").trim();
    const snapshot = await ref.get();

    if (!snapshot.exists) {
      res.status(403).json({ ok: false, message: "Mã không đúng hoặc đã hết hiệu lực." });
      return;
    }

    const data = snapshot.data();
    const now = Date.now();

    if (now > (data.expiresAt || 0)) {
      await ref.delete();
      res.status(403).json({ ok: false, message: "Mã đã hết hiệu lực. Hãy yêu cầu mã mới." });
      return;
    }

    if ((data.attempts || 0) >= MAX_ATTEMPTS) {
      await ref.delete();
      res.status(403).json({ ok: false, message: "Nhập sai quá nhiều lần. Hãy yêu cầu mã mới." });
      return;
    }

    const expected = data.codeHash;
    const actual = hashCode(email, code);
    const same = expected.length === actual.length &&
      crypto.timingSafeEqual(Buffer.from(expected, "hex"), Buffer.from(actual, "hex"));

    if (!same) {
      await ref.update({ attempts: admin.firestore.FieldValue.increment(1) });
      res.status(403).json({ ok: false, message: "Mã không đúng hoặc đã hết hiệu lực." });
      return;
    }

    await ref.delete();
    res.status(200).json({ ok: true });
    return;
  }

  res.status(404).json({ ok: false, message: "Đường dẫn không tồn tại." });
});
