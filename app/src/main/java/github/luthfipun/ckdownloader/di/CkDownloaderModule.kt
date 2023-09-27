package github.luthfipun.ckdownloader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.luthfipun.ck_downloader_core.core.CkDownloadManager
import github.luthfipun.ck_downloader_core.core.CkDownloadStandaloneDatabase
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CkDownloaderModule {
	@Singleton
	@Provides
	fun provideCkDownloadStandaloneDatabase(
		@ApplicationContext context: Context
	): CkDownloadStandaloneDatabase {
		return CkDownloadStandaloneDatabase(context)
			.build()
	}

	@Singleton
	@Provides
	fun provideCkDownloadManager(
		@ApplicationContext context: Context,
		ckDownloadStandaloneDatabase: CkDownloadStandaloneDatabase
	): CkDownloadManager {
		return CkDownloadManager(context, ckDownloadStandaloneDatabase)
	}

	@Singleton
	@Provides
	fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
		return HttpLoggingInterceptor().setLevel(
			HttpLoggingInterceptor.Level.BODY
		)
	}

	@Singleton
	@Provides
	fun provideOkHttp(
		httpLoggingInterceptor: HttpLoggingInterceptor
	): OkHttpClient {
		return OkHttpClient.Builder()
			.addInterceptor(httpLoggingInterceptor)
			.build()
	}
}