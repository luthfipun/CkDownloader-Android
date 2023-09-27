package github.luthfipun.ck_downloader_core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ck_download")
data class CkDownloadEntity(
	@PrimaryKey(autoGenerate = true)
	var id: Long? = null,
	@ColumnInfo(name = "unique_id", index = true)
	val uniqueId: String,
	@ColumnInfo(name = "url")
	val url: String,
	@ColumnInfo(name = "file_path")
	val filePath: String,
	@ColumnInfo(name = "content_length")
	val contentLength: Long,
	@ColumnInfo(name = "progress")
	val progress: Int,
	@ColumnInfo(name = "state")
	val state: String,
	@ColumnInfo(name = "created_at")
	val createdAt: Long = System.currentTimeMillis()
)
