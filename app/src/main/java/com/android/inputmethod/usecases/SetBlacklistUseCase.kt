package com.android.inputmethod.usecases

import arrow.core.Either
import com.lenguyenthanh.rxarrow.z
import io.reactivex.Single
import no.divvun.dictionary.personal.PersonalDictionaryDatabase

class SetBlacklistUseCase(val database: PersonalDictionaryDatabase) {
    fun execute(wordId: Long, blacklist: Boolean): Single<Either<BlacklistWordException, BlacklistWordSuccess>> {
        return database.dictionaryDao()
                .blacklistWord(wordId, blacklist)
                .map { BlacklistWordSuccess }
                .z {
                    BlacklistWordException.Unknown(it)
                }
    }
}

sealed class BlacklistWordException {
    data class Unknown(val cause: Throwable) : BlacklistWordException()
}

object BlacklistWordSuccess
