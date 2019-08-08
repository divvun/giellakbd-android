package no.divvun.dictionary.personal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.Single

@Dao
interface CandidatesDao {
    @Query("SELECT COUNT(word) FROM candidates WHERE word=:word AND language_id=:languageId")
    fun isCandidate(languageId: Long, word: String): Int

    @Insert
    fun insertCandidate(candidate: Candidate)

    @Query("DELETE FROM candidates WHERE word = :word AND language_id = :languageId")
    fun removeCandidate(languageId: Long, word: String): Int
}