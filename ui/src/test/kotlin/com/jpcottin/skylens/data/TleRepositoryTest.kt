package com.jpcottin.skylens.data

import com.jpcottin.skylens.ui.preview.PreviewSky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TleRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private var now = PreviewSky.NIGHT_MILLIS
    private val clock = Clock { now }

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun repository(bundled: (SatelliteGroup) -> String? = { if (it == SatelliteGroup.VISUAL) PreviewSky.tleText else null }) = TleRepository(
        cacheDir = folder.root,
        bundled = bundled,
        client = OkHttpClient(),
        clock = clock,
        baseUrl = server.url("/gp.php").toString(),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private val hstOnly = PreviewSky.tleText.lines().takeLast(3).joinToString("\n")

    @Test
    fun loadLocal_usesBundledDataWhenThereIsNoCache() = runTest {
        val repo = repository()
        repo.loadLocal(setOf(SatelliteGroup.VISUAL))
        val s = repo.state.value
        assertEquals(3, s.tles.size)
        assertEquals(TleSource.BUNDLED, s.source)
        assertNull(s.fetchedAtMillis)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun refresh_fetchesWritesCacheAndReportsNetwork() = runTest {
        server.enqueue(MockResponse(body = hstOnly))
        val repo = repository()
        repo.refresh(setOf(SatelliteGroup.VISUAL))
        val s = repo.state.value
        assertEquals(TleSource.NETWORK, s.source)
        assertEquals(1, s.tles.size)
        assertEquals("HST", s.tles[0].name)
        assertTrue(repo.cacheFile(SatelliteGroup.VISUAL).exists())
        assertEquals(now, s.fetchedAtMillis)
        val request = server.takeRequest()
        assertEquals("/gp.php?GROUP=visual&FORMAT=tle", request.url.encodedPath + "?" + request.url.encodedQuery)
    }

    @Test
    fun refresh_respectsTheTwoHourCourtesyInterval() = runTest {
        server.enqueue(MockResponse(body = hstOnly))
        val repo = repository()
        repo.refresh(setOf(SatelliteGroup.VISUAL))
        assertEquals(1, server.requestCount)
        now += 60 * 60 * 1000L
        repo.refresh(setOf(SatelliteGroup.VISUAL))
        assertEquals("no second request within two hours", 1, server.requestCount)
        now += 2 * 60 * 60 * 1000L
        server.enqueue(MockResponse(body = PreviewSky.tleText))
        repo.refresh(setOf(SatelliteGroup.VISUAL))
        assertEquals(2, server.requestCount)
        assertEquals(3, repo.state.value.tles.size)
    }

    @Test
    fun refresh_force_ignoresTheInterval() = runTest {
        server.enqueue(MockResponse(body = hstOnly))
        server.enqueue(MockResponse(body = hstOnly))
        val repo = repository()
        repo.refresh(setOf(SatelliteGroup.VISUAL))
        repo.refresh(setOf(SatelliteGroup.VISUAL), force = true)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun refresh_failureKeepsBundledDataAndReportsError() = runTest {
        server.enqueue(MockResponse(code = 503))
        val repo = repository()
        repo.refresh(setOf(SatelliteGroup.VISUAL))
        val s = repo.state.value
        assertEquals(TleSource.BUNDLED, s.source)
        assertEquals(3, s.tles.size)
        assertNotNull(s.error)
        assertTrue(s.error!!.contains("503"))
        assertFalse(s.refreshing)
        assertFalse(repo.cacheFile(SatelliteGroup.VISUAL).exists())
    }

    @Test
    fun refresh_emptyBodyIsAnError() = runTest {
        server.enqueue(MockResponse(body = "<html>maintenance</html>"))
        val repo = repository()
        repo.refresh(setOf(SatelliteGroup.VISUAL))
        assertNotNull(repo.state.value.error)
        assertEquals(TleSource.BUNDLED, repo.state.value.source)
    }

    @Test
    fun loadLocal_mergesGroupsAndDeduplicatesByCatalogueNumber() = runTest {
        val repo = repository { group ->
            when (group) {
                SatelliteGroup.VISUAL -> PreviewSky.tleText
                SatelliteGroup.STATIONS -> PreviewSky.tleText.lines().take(6).joinToString("\n") // ISS + Tianhe again
                else -> null
            }
        }
        repo.loadLocal(setOf(SatelliteGroup.VISUAL, SatelliteGroup.STATIONS))
        assertEquals(3, repo.state.value.tles.size)
    }

    @Test
    fun loadLocal_prefersCacheOverBundled() = runTest {
        val repo = repository()
        repo.cacheFile(SatelliteGroup.VISUAL).writeText(hstOnly)
        repo.loadLocal(setOf(SatelliteGroup.VISUAL))
        assertEquals(TleSource.CACHE, repo.state.value.source)
        assertEquals(1, repo.state.value.tles.size)
    }

    @Test
    fun refresh_cancelledMidFlight_clearsRefreshing() = runTest {
        lateinit var job: Job
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            job.cancel()   // the group setting changed while the fetch was in flight
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(hstOnly.toResponseBody("text/plain".toMediaType())).build()
        }.build()
        val repo = TleRepository(folder.root, { PreviewSky.tleText }, client, clock, server.url("/gp.php").toString(), ioDispatcher = Dispatchers.Unconfined)
        job = launch { repo.refresh(setOf(SatelliteGroup.VISUAL)) }
        runCurrent()
        repo.refresh(setOf(SatelliteGroup.VISUAL))          // the cache is fresh now: the early-return path
        assertFalse(repo.state.value.refreshing)
    }
}
