package github.luthfipun.ck_downloader_core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import github.luthfipun.ck_downloader_core.db.dao.CkDownloadDao
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity

@Database(
	entities = [
		CkDownloadEntity::class
	],
	version = 1
)
abstract class CkDownloadDatabase : RoomDatabase() {
	abstract fun ckDownloadDao(): CkDownloadDao
}