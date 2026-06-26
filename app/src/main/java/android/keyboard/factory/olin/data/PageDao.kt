package android.keyboard.factory.olin.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Insert
    suspend fun insert(page: PageEntity): Long

    @Update
    suspend fun update(page: PageEntity)

    @Delete
    suspend fun delete(page: PageEntity)

    @Query("SELECT * FROM page WHERE projectId = :projectId ORDER BY pageIndex ASC")
    fun observeForProject(projectId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM page WHERE projectId = :projectId ORDER BY pageIndex ASC")
    suspend fun getForProject(projectId: Long): List<PageEntity>

    @Query("SELECT * FROM page WHERE id = :pageId")
    suspend fun getById(pageId: Long): PageEntity?

    @Query("SELECT COALESCE(MAX(pageIndex), -1) FROM page WHERE projectId = :projectId")
    suspend fun maxPageIndex(projectId: Long): Int
}
