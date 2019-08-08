package no.divvun.service

import io.reactivex.Single
import no.divvun.domain.DictionaryJson
import retrofit2.http.Body
import retrofit2.http.POST

interface DivvunDictionaryUploadService {
    companion object {
        const val BASE_URL = "https://www.uit.no/"
    }
    @POST("/dictionary/upload")
    fun uploadDictionary(@Body dictionaryJson: DictionaryJson): Single<DictionaryJson>
}
