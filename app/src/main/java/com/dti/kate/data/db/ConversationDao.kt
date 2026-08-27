package com.dti.kate.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(turn: ConversationTurn)

    /** Most recent turns first, newest N - callers reverse for chronological order. */
    @Query("SELECT * FROM conversation_turns ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ConversationTurn>

    /** House-keeping: keeps the table from growing unbounded on long-lived
     * installs. Called after each insert with a generous cap - this is
     * short-term memory for context resolution, not a permanent transcript
     * (HistoryScreen/JournalDao, if/when it exists, would be the permanent
     * record). */
    @Query(
        "DELETE FROM conversation_turns WHERE id NOT IN " +
            "(SELECT id FROM conversation_turns ORDER BY id DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM conversation_turns")
    suspend fun clearAll()
}
