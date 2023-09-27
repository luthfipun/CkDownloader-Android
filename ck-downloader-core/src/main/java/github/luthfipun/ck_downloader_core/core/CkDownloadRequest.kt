package github.luthfipun.ck_downloader_core.core

import java.io.File

data class CkDownloadRequest(
	val uniqueId: String,
	val fileName: String,
	val extension: String? = null,
	val url: String,
	val contentLength: Long,
	val path: File? = null
)