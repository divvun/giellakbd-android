package com.android.inputmethod.latin.utils

import android.content.Context
import android.os.Build
import com.android.inputmethod.latin.R
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
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

            Sentry.init(sentryDsn, AndroidSentryClientFactory(context.applicationContext))

            val sentry = Sentry.getContext()

            sentry.addTag("locale", currentLocale(context).toString())
        }

        @JvmStatic
        val sentry: io.sentry.context.Context get() = Sentry.getContext()
    }
}