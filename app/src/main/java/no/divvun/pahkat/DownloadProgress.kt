package no.divvun.pahkat

import java.io.Serializable

sealed class DownloadProgress: Serializable {
    data class Starting(val packageKey: String): DownloadProgress()
    data class Downloading(
            val packageKey: String,
            val current: Long,
            val total: Long
    ): DownloadProgress()
    data class Cancelled(val packageKey: String): DownloadProgress()
    data class Completed(val packageKey: String, val path: String): DownloadProgress()
    data class Error(val packageKey: String, val value: Throwable): DownloadProgress()

    val isFinished: Boolean get() = when (this) {
        is Cancelled, is Completed, is Error -> true
        else -> false
    }

    val isCompleted: Boolean get() = this is Completed
    val destPath: String? get() = if (this is Completed) { this.path } else { null }
}