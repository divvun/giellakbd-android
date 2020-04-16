package no.divvun.pahkat

import android.content.Context
import androidx.work.Data
import androidx.work.WorkManager
import no.divvun.pahkat.client.PackageKey
import java.io.*

sealed class UpdateProgress : Serializable {
    sealed class Download : Serializable, UpdateProgress() {
        data class Starting(val packageKey: String) : Download()
        data class Downloading(
                val packageKey: String,
                val current: Long,
                val total: Long
        ) : Download()

        data class Cancelled(val packageKey: String) : Download()
        data class Completed(val packageKey: String, val path: String) : Download()
        data class Error(val packageKey: String, val value: Throwable) : Download()

        val isFinished: Boolean
            get() = when (this) {
                is Cancelled, is Completed, is Error -> true
                else -> false
            }

        val isCompleted: Boolean get() = this is Completed
        val destPath: String?
            get() = if (this is Completed) {
                this.path
            } else {
                null
            }
    }

    sealed class TransactionProgress : Serializable, UpdateProgress() {
        object Completed : TransactionProgress() {
            override fun toString() = "Completed"
        }

        object Cancelled : TransactionProgress() {
            override fun toString() = "Cancelled"
        }

        data class Install(val packageKey: PackageKey) : TransactionProgress()
        data class Uninstall(val packageKey: PackageKey) : TransactionProgress()
        data class UnknownEvent(val event: Long) : TransactionProgress()
        data class Error(val packageKey: PackageKey?, val error: java.lang.Exception?) :
                TransactionProgress()

        val isFinished get() = false
    }
}

