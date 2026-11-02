package github.luthfipun.ck_downloader_core.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Downloads a file range by range. The write offset is always taken from the file length,
 * a failed chunk is truncated away and retried, so the output never contains shifted data.
 */
internal class CkChunkDownloader(
	private val client: OkHttpClient,
	private val maxRetryPerChunk: Int = DEFAULT_MAX_RETRY_PER_CHUNK,
	private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
	private val chunkSizeFor: (Long) -> Long = ::downloadMaxChunkLength,
	private val onCallCreated: (Call) -> Unit = {},
	private val onProgress: suspend (Int) -> Unit = {}
) {

	sealed class Result {
		data object Success : Result()
		data class Failed(val reason: String) : Result()
	}

	private sealed class Outcome {
		/** 206 with [written] bytes appended, [total] is the size reported in Content-Range if any. */
		data class Chunk(val written: Long, val total: Long?) : Outcome()
		/** 200 at offset 0, the whole file was written. */
		data object Complete : Outcome()
		/** 416, nothing left to read at this offset. */
		data object EndOfFile : Outcome()
		/** Transient problem, the chunk can be requested again. */
		data class Retry(val reason: String) : Outcome()
		/** Permanent problem such as an expired URL (403/410). */
		data class Fatal(val reason: String) : Outcome()
	}

	/**
	 * Downloads [url] into [outputFile], which is expected to be empty or missing.
	 * [contentLength] is only a hint, the total reported by the server takes precedence.
	 * Throws CancellationException when the coroutine is cancelled.
	 */
	suspend fun download(url: String, contentLength: Long, outputFile: File): Result {
		var total: Long? = contentLength.takeIf { it > 0 }
		var serverTotal: Long? = null
		var chunkSize = chunkSizeFor(total ?: UNKNOWN_LENGTH_HINT)
		var attempt = 0

		while (true) {
			val start = outputFile.length()
			val currentTotal = total
			if (currentTotal != null && start >= currentTotal) break

			val end = if (currentTotal != null) minOf(start + chunkSize, currentTotal) - 1L else start + chunkSize - 1L
			val requested = end - start + 1L

			val outcome = try {
				fetchRange(url, start, end, outputFile)
			} catch (e: IOException) {
				// A cancelled call also surfaces as IOException, stop instead of retrying
				currentCoroutineContext().ensureActive()
				Outcome.Retry("${e.javaClass.simpleName}: ${e.message}")
			}

			when (outcome) {
				is Outcome.Chunk -> {
					attempt = 0
					if (outcome.total != null) {
						serverTotal = outcome.total
						if (outcome.total != total) {
							total = outcome.total
							chunkSize = chunkSizeFor(outcome.total)
						}
					}
					total?.let { onProgress((outputFile.length().times(100)).div(it).toInt()) }
					// Without a known size, a chunk shorter than requested is the end of the file
					if (total == null && outcome.written < requested) break
				}
				Outcome.Complete -> return Result.Success
				// 416 right after the last byte when the size is unknown and a multiple of chunkSize
				Outcome.EndOfFile -> {
					if (total == null && start > 0) break
					return Result.Failed("HTTP 416 at offset $start")
				}
				is Outcome.Retry -> {
					truncate(outputFile, start)
					attempt++
					if (attempt > maxRetryPerChunk) {
						return Result.Failed("Chunk at offset $start failed after $maxRetryPerChunk retries: ${outcome.reason}")
					}
					delay(retryDelayMs.times(1L shl (attempt - 1)))
				}
				is Outcome.Fatal -> return Result.Failed(outcome.reason)
			}
		}

		// Only the size reported by the server is trusted, the caller's hint may be an estimate
		val expected = serverTotal
		if (expected != null && outputFile.length() != expected) {
			return Result.Failed("Size mismatch, expected $expected but got ${outputFile.length()}")
		}
		return Result.Success
	}

	private suspend fun fetchRange(url: String, start: Long, end: Long, outputFile: File): Outcome {
		val request = Request.Builder()
			.url(url)
			.header("Range", "bytes=$start-$end")
			.build()
		val call = client.newCall(request)
		onCallCreated(call)
		// Re-check after publishing the call so a concurrent stop can't miss it
		currentCoroutineContext().ensureActive()

		return call.execute().use { r ->
			val body = r.body ?: return@use Outcome.Retry("Empty body, HTTP ${r.code}")
			when (r.code) {
				206 -> {
					val range = r.header("Content-Range")?.let { CONTENT_RANGE_REGEX.find(it) }
					val rangeStart = range?.groupValues?.get(1)?.toLongOrNull()
					val rangeEnd = range?.groupValues?.get(2)?.toLongOrNull()
					val rangeTotal = range?.groupValues?.get(3)?.toLongOrNull()
					// A response for another offset would leave a gap or overlap in the file
					if (rangeStart != null && rangeStart != start) {
						return@use Outcome.Retry("Content-Range starts at $rangeStart, requested $start")
					}

					val written = saveChunkToFile(body.byteStream(), outputFile, append = true)
					val expected = if (rangeStart != null && rangeEnd != null) rangeEnd - rangeStart + 1L else null
					when {
						written <= 0L -> Outcome.Retry("Empty chunk at offset $start")
						expected != null && written != expected -> Outcome.Retry("Chunk at offset $start has $written of $expected bytes")
						else -> Outcome.Chunk(written, rangeTotal)
					}
				}
				// Server ignored the Range header. At offset 0 the body is the whole file,
				// later on appending it would duplicate data, so retry the chunk instead
				200 -> {
					if (start != 0L) return@use Outcome.Retry("HTTP 200 at offset $start")
					saveChunkToFile(body.byteStream(), outputFile, append = false)
					Outcome.Complete
				}
				416 -> Outcome.EndOfFile
				408, 429 -> Outcome.Retry("HTTP ${r.code}")
				in 500..599 -> Outcome.Retry("HTTP ${r.code}")
				else -> Outcome.Fatal("HTTP ${r.code}")
			}
		}
	}

	/**
	 * Copies [inputStream] into [outputFile] and returns the number of bytes written.
	 * IO errors are propagated so the caller can truncate and retry the chunk.
	 */
	private suspend fun saveChunkToFile(
		inputStream: InputStream,
		outputFile: File,
		append: Boolean
	): Long {
		var total = 0L
		FileOutputStream(outputFile, append).use { outputStream ->
			val buffer = ByteArray(8192)
			var bytesRead: Int
			while (inputStream.read(buffer).also { bytesRead = it } != -1) {
				currentCoroutineContext().ensureActive()
				outputStream.write(buffer, 0, bytesRead)
				total += bytesRead
			}
			outputStream.flush()
		}
		return total
	}

	private fun truncate(file: File, length: Long) {
		if (file.exists() && file.length() != length) {
			RandomAccessFile(file, "rw").use { it.setLength(length) }
		}
	}

	companion object {
		const val DEFAULT_MAX_RETRY_PER_CHUNK = 5
		const val DEFAULT_RETRY_DELAY_MS = 1000L
		private const val UNKNOWN_LENGTH_HINT = 30_000_000L
		private val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

		fun downloadMaxChunkLength(contentLength: Long): Long {
			val m = (1000.times(1000)).toLong()
			return when {
				contentLength <= 50.times(m) -> 2.times(m)
				contentLength <= 100.times(m) -> 4.times(m)
				contentLength <= 500.times(m) -> 5.times(m)
				else -> 8.times(m)
			}
		}
	}
}
