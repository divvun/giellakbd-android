package no.divvun.pahkat

import android.content.Context
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import no.divvun.pahkat.client.PackageKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

fun Context.workManager(): WorkManager = WorkManager.getInstance(this)

fun PackageKey.workName(): String {
    return "download-${this}"
}

fun PackageKey.workData(path: String): Data {
    return Data.Builder()
            .putString(KEY_PACKAGE_KEY, this.toString())
            .putString(KEY_PACKAGE_STORE_PATH, path)
            .build()
}

inline fun <reified T> T.toData(): Data {
    val baos = ByteArrayOutputStream()
    val oos = ObjectOutputStream(baos)

    oos.writeObject(this)
    oos.flush()

    return Data.Builder()
            .putString(KEY_OBJECT_TYPE, T::class.java.canonicalName)
            .putByteArray(KEY_OBJECT, baos.toByteArray())
            .build()
}

inline fun <reified T> Data.into(): T? {
    val bytes = this.getByteArray(KEY_OBJECT) ?: return null
    val bais = ByteArrayInputStream(bytes)
    val ois = ObjectInputStream(bais)
    return ois.readObject() as? T
}

fun List<WorkInfo>.workEnabled() = isNotEmpty() && any { it.state != WorkInfo.State.CANCELLED }
fun List<WorkInfo>.isRunning() = isNotEmpty() && any { it.state != WorkInfo.State.RUNNING }


