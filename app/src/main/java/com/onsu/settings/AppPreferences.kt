package com.onsu.settings

import android.content.Context
import com.onsu.i18n.AppLanguage
import com.onsu.ui.theme.AppThemeMode

/**
 * Tuỳ chọn giao diện được lưu trên máy, tách khỏi dữ liệu tài khoản.
 *
 * Dùng SharedPreferences thay vì Room: hai giá trị đơn giản, cần đọc ngay trước
 * khi composition đầu tiên chạy (để không bị nháy sai theme/ngôn ngữ).
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = prefs.getString(KEY_LANGUAGE, null)
            ?.let { stored -> AppLanguage.entries.firstOrNull { it.name == stored } }
            ?: AppLanguage.ENGLISH
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.name).apply()
        }

    var themeMode: AppThemeMode
        get() = prefs.getString(KEY_THEME, null)
            ?.let { stored -> AppThemeMode.entries.firstOrNull { it.name == stored } }
            ?: AppThemeMode.DARK
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    private companion object {
        const val FILE_NAME = "onsu_ui_settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
    }
}
