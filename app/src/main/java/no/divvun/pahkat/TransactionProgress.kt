package no.divvun.pahkat

import no.divvun.pahkat.client.PackageKey
import java.io.Serializable

sealed class TransactionProgress: Serializable {
    object Completed: TransactionProgress() { override fun toString() = "Completed" }
    object Cancelled: TransactionProgress() { override fun toString() = "Cancelled" }
    data class Install(val packageKey: PackageKey): TransactionProgress()
    data class Uninstall(val packageKey: PackageKey): TransactionProgress()
    data class UnknownEvent(val event: Long): TransactionProgress()
    data class Error(val packageKey: PackageKey?, val error: java.lang.Exception?): TransactionProgress()

    val isFinished get() = false
}