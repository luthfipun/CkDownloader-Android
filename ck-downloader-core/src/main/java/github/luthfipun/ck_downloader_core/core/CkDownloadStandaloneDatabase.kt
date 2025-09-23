package github.luthfipun.ck_downloader_core.core

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import github.luthfipun.ck_downloader_core.db.CkDownloadDatabase
import javax.inject.Inject

class CkDownloadStandaloneDatabase @Inject constructor(
	@param:ApplicationContext private val context: Context
) {
	var db: CkDownloadDatabase? = null
	fun build(): CkDownloadStandaloneDatabase {
		db = Room.databaseBuilder(
				context, CkDownloadDatabase::class.java, "ck_downloader_db"
			).fallbackToDestructiveMigration(false)
			.build()
		return this
	}
}