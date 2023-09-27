package github.luthfipun.ck_downloader_core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import github.luthfipun.ck_downloader_core.core.CkDownloadModelProgress
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import kotlinx.coroutines.flow.Flow

@Dao
interface CkDownloadDao {
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertOne(ckDownloadEntity: CkDownloadEntity)

	@Query("DELETE FROM ck_download WHERE unique_id = :uniqueId")
	suspend fun deleteByUniqueId(uniqueId: String)

	@Query("DELETE FROM ck_download")
	suspend fun clean()

	@Query("UPDATE ck_download SET state = :state WHERE unique_id = :uniqueId")
	suspend fun updateState(uniqueId: String, state: String)

	@Query("UPDATE ck_download SET progress = :progress WHERE unique_id = :uniqueId")
	suspend fun updateProgress(uniqueId: String, progress: Int)

	@Query("SELECT * FROM ck_download WHERE unique_id = :uniqueId")
	suspend fun getByUniqueId(uniqueId: String): CkDownloadEntity?

	@Query("SELECT COUNT(*) FROM ck_download WHERE unique_id = :uniqueId")
	suspend fun checkByUniqueId(uniqueId: String): Int

	@Query("SELECT SUM(progress)/COUNT(*) FROM ck_download WHERE state = :state")
	fun getTotalProgress(state: String = CkDownloadState.STATE_DOWNLOADING.name): Flow<Int?>

	@Query("SELECT * FROM ck_download ORDER BY created_at ASC")
	suspend fun getAllDownloads(): List<CkDownloadEntity>

	@Query("SELECT unique_id as uniqueId, progress FROM ck_download WHERE state = :state")
	suspend fun getProgress(state: String = CkDownloadState.STATE_DOWNLOADING.name): List<CkDownloadModelProgress>

	@Query("SELECT unique_id as uniqueId, progress FROM ck_download WHERE state = :state")
	fun getProgressFlow(state: String = CkDownloadState.STATE_DOWNLOADING.name): Flow<List<CkDownloadModelProgress>>
}