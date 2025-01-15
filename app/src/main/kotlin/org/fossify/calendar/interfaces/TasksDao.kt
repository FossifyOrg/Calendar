package org.fossify.calendar.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.calendar.models.Task

@Dao
interface TasksDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(task: Task): Long

    @Query("SELECT * FROM tasks WHERE task_id = :id AND start_ts = :startTs")
    fun getTaskWithIdAndTs(id: Long, startTs: Long): Task?

    @Query("DELETE FROM tasks WHERE task_id = :id AND start_ts = :startTs")
    fun deleteTaskWithIdAndTs(id: Long, startTs: Long)

    @Query("DELETE FROM tasks WHERE task_id = :id AND start_ts >= :startTs")
    fun deleteTaskFutureOccurrences(id: Long, startTs: Long)
}
