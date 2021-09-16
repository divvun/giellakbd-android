package com.android.inputmethod.latin.utils

import android.content.Context
import android.os.Build
import com.android.inputmethod.latin.R
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import java.util.*

/**
 * Created by brendan on 9/9/17.
 */

class ExceptionLogger {
    companion object {
        private fun currentLocale(context: Context): Locale {
            @Suppress("DEPRECATION")
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                context.resources.configuration.locale
            }
        }

        @JvmStatic
        fun init(context: Context) {
            val sentryDsn = context.getString(R.string.sentry_dsn) ?: return

            if (sentryDsn == "") {
                return
            }

            SentryAndroid.init(context.applicationContext) {
                it.dsn = sentryDsn
                it.setTag("locale", currentLocale(context).toString())
            }
        }
    }
}