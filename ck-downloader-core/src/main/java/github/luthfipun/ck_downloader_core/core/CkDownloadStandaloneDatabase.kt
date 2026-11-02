package github.luthfipun.ck_downloader_core.core

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import github.luthfipun.ck_downloader_core.db.CkDownloadDatabase
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import java.util.concurrent.atomic.AtomicBoolean

class CkDownloadStandaloneDatabase(
	context: Context
) {
	private val appContext = context.applicationContext

	/** Built on first access, calling [build] is optional. */
	val db: CkDownloadDatabase by lazy {
		Room.databaseBuilder(
				appContext, CkDownloadDatabase::class.java, "ck_downloader_db"
			).addCallback(recoverInterruptedDownloads)
			.fallbackToDestructiveMigration(false)
			.build()
	}

	fun build(): CkDownloadStandaloneDatabase {
		db
		return this
	}

	companion object {
		private val recovered = AtomicBoolean(false)

		/**
		 * Downloads run inside this process, so when the database is first opened
		 * no download can be running. Rows left in STATE_DOWNLOADING were interrupted
		 * by process death and are marked as error so they can be retried.
		 */
		private val recoverInterruptedDownloads = object : RoomDatabase.Callback() {
			override fun onOpen(db: SupportSQLiteDatabase) {
				if (recovered.compareAndSet(false, true)) {
					db.execSQL(
						"UPDATE ck_download SET state = ? WHERE state = ?",
						arrayOf(CkDownloadState.STATE_ERROR.name, CkDownloadState.STATE_DOWNLOADING.name)
					)
				}
			}
		}
	}
}
