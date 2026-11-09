package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.runtime.mutableStateOf
import io.github.whywood.deepdeep.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrScanTest {
    @Test
    fun scanningClearsResultAndCountsAttempt() {
        val initial = QrScanState(
            status = QrScanStatus.Found,
            codes = listOf("old"),
            target = QrScanTarget.LocalFile("/tmp/old.png"),
            attempt = 2,
        )
        val scanning = initial.scanning(QrScanTarget.RemoteUrl("https://example.com/a.png"))

        assertEquals(QrScanStatus.Scanning, scanning.status)
        assertEquals(emptyList<String>(), scanning.codes)
        assertEquals(QrScanTarget.RemoteUrl("https://example.com/a.png"), scanning.target)
        assertEquals(3, scanning.attempt)
    }

    @Test
    fun completedMapsResultToStatus() {
        val scanning = QrScanState(status = QrScanStatus.Scanning)

        assertEquals(QrScanStatus.Failed, scanning.completed(null).status)
        assertEquals(emptyList<String>(), scanning.completed(null).codes)

        assertEquals(QrScanStatus.Empty, scanning.completed(emptyList()).status)
        assertEquals(emptyList<String>(), scanning.completed(emptyList()).codes)

        val found = scanning.completed(listOf("https://example.com", "文本"))
        assertEquals(QrScanStatus.Found, found.status)
        assertEquals(listOf("https://example.com", "文本"), found.codes)
    }

    @Test
    fun saveableListRoundTripsEveryTarget() {
        val states = listOf(
            QrScanState(),
            QrScanState(
                status = QrScanStatus.Found,
                codes = listOf("https://example.com/a", "纯文本"),
                target = QrScanTarget.RemoteUrl("https://example.com/qr.png"),
                attempt = 2,
            ),
            QrScanState(
                status = QrScanStatus.Empty,
                target = QrScanTarget.LocalFile("/data/user/0/app/cache/qr-scan/qr-1.img"),
                attempt = 5,
            ),
            QrScanState(
                status = QrScanStatus.Failed,
                target = QrScanTarget.LocalFile("/data/file:odd.png"),
                attempt = 1,
            ),
        )
        for (state in states) {
            assertEquals(state, qrScanStateFromSaveable(state.toSaveableList()))
        }
    }

    @Test
    fun saveableListKeepsCodesAsArrayList() {
        val state = QrScanState(status = QrScanStatus.Found, codes = listOf("a", "b"))
        val saved = state.toSaveableList()

        assertEquals(ArrayList(listOf("a", "b")), saved[2])
        assertEquals(state, qrScanStateFromSaveable(saved))
    }

    @Test
    fun saveableListRejectsMalformedValues() {
        assertNull(qrScanStateFromSaveable(emptyList()))
        assertNull(qrScanStateFromSaveable(listOf(0, 0)))
        assertNull(qrScanStateFromSaveable(listOf(99, 0, ArrayList<String>(), "")))
        assertNull(qrScanStateFromSaveable(listOf(0, "x", ArrayList<String>(), "")))
        assertNull(qrScanStateFromSaveable(listOf(0, 0, null, null)))
        assertNull(qrScanStateFromSaveable(listOf(0, 0, ArrayList<String>(), "ftp://example.com")))
    }

    @Test
    fun remoteImageUrlAcceptsOnlyHttpSources() {
        assertEquals("https://example.com/a.png", remoteImageUrl("https://example.com/a.png"))
        assertEquals("http://example.com/a.png", remoteImageUrl("http://example.com/a.png"))
        assertEquals("HTTPS://EXAMPLE.COM/A.PNG", remoteImageUrl("HTTPS://EXAMPLE.COM/A.PNG"))
        assertEquals("https://example.com/a.png", remoteImageUrl("  https://example.com/a.png\n"))
        assertNull(remoteImageUrl("file:///sdcard/a.png"))
        assertNull(remoteImageUrl("/sdcard/a.png"))
        assertNull(remoteImageUrl("data:image/png;base64,AAAA"))
        assertNull(remoteImageUrl(""))
        assertNull(remoteImageUrl("   "))
    }

    @Test
    fun normalizeQrCodesDropsBlankAndDuplicates() {
        val normalized = normalizeQrCodes(listOf("https://a", null, " ", "b", "https://a", "", "b"))
        assertEquals(listOf("https://a", "b"), normalized)
        assertEquals(emptyList<String>(), normalizeQrCodes(emptyList()))
    }

    @Test
    fun copyLabelUsesIndexForMultipleCodes() {
        assertEquals(R.string.files_qr_copy, qrCopyLabelRes(index = 0, count = 1))
        assertEquals(R.string.files_qr_copy_index, qrCopyLabelRes(index = 0, count = 2))
        assertEquals(R.string.files_qr_copy_index, qrCopyLabelRes(index = 3, count = 4))
    }

    @Test
    fun scannerIgnoresRequestsAfterScanStarted() {
        val stateRef = mutableStateOf(QrScanState())
        val scanner = QrScanner(stateRef)
        val first = QrScanTarget.LocalFile("/tmp/a.png")

        scanner.requestScan(first)
        assertEquals(QrScanStatus.Scanning, scanner.state.status)
        assertEquals(1, scanner.state.attempt)

        scanner.requestScan(QrScanTarget.RemoteUrl("https://example.com/b.png"))
        assertEquals(first, scanner.state.target)
        assertEquals(1, scanner.state.attempt)

        stateRef.value = scanner.state.completed(emptyList())
        scanner.requestScan(QrScanTarget.RemoteUrl("https://example.com/b.png"))
        assertEquals(QrScanStatus.Empty, scanner.state.status)
        assertEquals(1, scanner.state.attempt)

        stateRef.value = stateRef.value.completed(listOf("x"))
        scanner.requestScan(QrScanTarget.RemoteUrl("https://example.com/b.png"))
        assertEquals(QrScanStatus.Found, scanner.state.status)
        assertEquals(first, scanner.state.target)
    }

    @Test
    fun scannerRestartsFailedScan() {
        val stateRef = mutableStateOf(QrScanState())
        val scanner = QrScanner(stateRef)

        scanner.retry()
        assertEquals(QrScanState(), scanner.state)

        scanner.requestScan(QrScanTarget.LocalFile("/tmp/a.png"))
        stateRef.value = scanner.state.completed(null)
        assertEquals(QrScanStatus.Failed, scanner.state.status)

        scanner.requestScan(QrScanTarget.RemoteUrl("https://example.com/b.png"))
        assertEquals(QrScanStatus.Scanning, scanner.state.status)
        assertEquals(QrScanTarget.RemoteUrl("https://example.com/b.png"), scanner.state.target)
        assertEquals(2, scanner.state.attempt)

        stateRef.value = scanner.state.completed(null)
        scanner.retry()
        assertEquals(QrScanStatus.Scanning, scanner.state.status)
        assertEquals(3, scanner.state.attempt)
    }
}
