package github.luthfipun.ck_downloader_core.service

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class CkChunkDownloaderTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	private val server = MockWebServer()
	private val source = Random(42).nextBytes(300_000)
	private val requestCount = AtomicInteger(0)

	/** Overrides the response for a given request index (1-based), null keeps the normal 206. */
	private var fault: (index: Int, start: Long, end: Long) -> MockResponse? = { _, _, _ -> null }

	// Mirrors the client's OkHttp setup: no automatic retry, short timeouts
	private val client = OkHttpClient.Builder()
		.retryOnConnectionFailure(false)
		.readTimeout(2, TimeUnit.SECONDS)
		.build()

	@Before
	fun setUp() {
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				val index = requestCount.incrementAndGet()
				val (start, end) = parseRange(request.getHeader("Range"))
				return fault(index, start, end) ?: partial(start, end)
			}
		}
		server.start()
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	private fun parseRange(header: String?): Pair<Long, Long> {
		val match = Regex("""bytes=(\d+)-(\d+)""").find(header.orEmpty()) ?: return 0L to source.size - 1L
		return match.groupValues[1].toLong() to match.groupValues[2].toLong()
	}

	private fun partial(start: Long, end: Long): MockResponse {
		if (start >= source.size) return MockResponse().setResponseCode(416)
		val last = minOf(end, source.size - 1L)
		return MockResponse()
			.setResponseCode(206)
			.setHeader("Content-Range", "bytes $start-$last/${source.size}")
			.setBody(Buffer().write(source, start.toInt(), (last - start + 1).toInt()))
	}

	private fun full(): MockResponse = MockResponse().setResponseCode(200).setBody(Buffer().write(source))

	private fun download(contentLength: Long, maxRetry: Int = 3): Pair<CkChunkDownloader.Result, ByteArray> {
		val file = tempFolder.newFile()
		file.delete()
		val downloader = CkChunkDownloader(
			client = client,
			maxRetryPerChunk = maxRetry,
			retryDelayMs = 1L,
			chunkSizeFor = { 64_000L }
		)
		val result = runBlocking { downloader.download(server.url("/video.mp4").toString(), contentLength, file) }
		return result to (if (file.exists()) file.readBytes() else ByteArray(0))
	}

	private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

	private fun assertIdentical(bytes: ByteArray) {
		assertEquals(source.size, bytes.size)
		assertArrayEquals(sha256(source), sha256(bytes))
	}

	@Test
	fun `downloads all chunks byte identical`() {
		val (result, bytes) = download(source.size.toLong())
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
	}

	@Test
	fun `disconnect in the middle of a chunk body is retried without shifting data`() {
		fault = { index, start, end ->
			if (index == 3) partial(start, end).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) else null
		}
		val (result, bytes) = download(source.size.toLong())
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
		// 5 chunks plus one retried request
		assertEquals(6, requestCount.get())
	}

	@Test
	fun `body cut off on every chunk fails once retries run out`() {
		fault = { index, start, end ->
			if (index >= 2) partial(start, end).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) else null
		}
		val (result, _) = download(source.size.toLong(), maxRetry = 2)
		assertTrue(result is CkChunkDownloader.Result.Failed)
	}

	@Test
	fun `200 in the middle of the download does not duplicate data`() {
		fault = { index, _, _ -> if (index == 3) full() else null }
		val (result, bytes) = download(source.size.toLong())
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
		// 5 chunks plus one retried request
		assertEquals(6, requestCount.get())
	}

	@Test
	fun `server ignoring Range from the start returns the whole file`() {
		fault = { _, _, _ -> full() }
		val (result, bytes) = download(source.size.toLong())
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
	}

	@Test
	fun `403 in the middle fails without retry`() {
		fault = { index, _, _ -> if (index == 3) MockResponse().setResponseCode(403) else null }
		val (result, _) = download(source.size.toLong())
		assertTrue(result is CkChunkDownloader.Result.Failed)
		assertEquals(3, requestCount.get())
	}

	@Test
	fun `server error is retried`() {
		fault = { index, _, _ -> if (index == 2) MockResponse().setResponseCode(503) else null }
		val (result, bytes) = download(source.size.toLong())
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
		// 5 chunks plus one retried request
		assertEquals(6, requestCount.get())
	}

	@Test
	fun `Content-Range for another offset is retried`() {
		fault = { index, start, end -> if (index == 2) partial(start + 10, end) else null }
		val (result, bytes) = download(source.size.toLong())
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
		// 5 chunks plus one retried request
		assertEquals(6, requestCount.get())
	}

	@Test
	fun `contentLength larger than the real size uses the server total`() {
		val (result, bytes) = download(30_000_000L)
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
	}

	@Test
	fun `contentLength smaller than the real size uses the server total`() {
		val (result, bytes) = download(100_000L)
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
	}

	@Test
	fun `unknown contentLength downloads the whole file`() {
		val (result, bytes) = download(0L)
		assertEquals(CkChunkDownloader.Result.Success, result)
		assertIdentical(bytes)
	}

	@Test
	fun `server sending less data than its declared total fails`() {
		// Content-Range claims a total larger than what the server can actually serve
		fault = { _, start, end ->
			val last = minOf(end, source.size - 1L)
			if (start >= source.size) MockResponse().setResponseCode(416)
			else MockResponse()
				.setResponseCode(206)
				.setHeader("Content-Range", "bytes $start-$last/${source.size + 500}")
				.setBody(Buffer().write(source, start.toInt(), (last - start + 1).toInt()))
		}
		val (result, _) = download(source.size.toLong())
		assertTrue(result is CkChunkDownloader.Result.Failed)
	}
}
