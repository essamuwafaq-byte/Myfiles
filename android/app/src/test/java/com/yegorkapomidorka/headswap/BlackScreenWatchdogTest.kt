package com.yegorkapomidorka.headswap

import com.yegorkapomidorka.headswap.BlackScreenWatchdog.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackScreenWatchdogTest {
    private val t0 = 100_000L

    private fun check(
        now: Long,
        huBytes: Long = 500,
        start: Long = t0 + 1_000,
        paused: Boolean = false,
        first: Long = 0,
        last: Long = 0,
        staticFrames: Boolean = true,
        huStart: Long = start,
    ) = BlackScreenWatchdog.check(now, t0, huBytes, start, paused, first, last, staticFrames, huStart)

    @Test
    fun silentHeadUnitFiresAfterTimeout() {
        assertNull(check(t0 + BlackScreenWatchdog.NO_HU_BYTES_MS - 1, huBytes = 0, start = 0))
        assertEquals(Verdict.NO_HU_BYTES, check(t0 + BlackScreenWatchdog.NO_HU_BYTES_MS, huBytes = 0, start = 0))
    }

    @Test
    fun noStartAtAllIsBlackScreen() {
        assertNull(check(t0 + BlackScreenWatchdog.NO_HU_START_MS - 1, start = 0))
        assertEquals(Verdict.NO_HU_START, check(t0 + 120_000, start = 0))
    }

    @Test
    fun startThenPauseIsUserInCarMenusNotBlack() {
        // ГУ прислала START, потом PAUSE (радио, меню машины): не трогаем сколько угодно
        assertNull(check(t0 + 600_000, start = t0 + 1_500, paused = true, first = t0 + 2_000, last = t0 + 60_000, huStart = t0 + 1_500))
    }

    @Test
    fun noPictureAfterStart() {
        val start = t0 + 2_000
        assertNull(check(start + BlackScreenWatchdog.NO_PICTURE_MS - 1, start = start))
        assertEquals(Verdict.NO_PICTURE, check(start + BlackScreenWatchdog.NO_PICTURE_MS, start = start))
    }

    @Test
    fun pausedNeverFires() {
        assertNull(check(t0 + 600_000, paused = true))
        assertNull(check(t0 + 600_000, paused = true, first = t0 + 3_000, last = t0 + 3_000))
    }

    @Test
    fun resumeFromPauseRestartsTheClock() {
        // картинка была, ГУ ушла в меню на 5 минут и вернулась: отсчёт от нового START
        val resume = t0 + 300_000
        assertNull(check(resume + 5_000, start = resume, first = t0 + 3_000, last = t0 + 10_000))
        assertEquals(Verdict.PICTURE_STALL,
            check(resume + BlackScreenWatchdog.PICTURE_STALL_MS, start = resume, first = t0 + 3_000, last = t0 + 10_000))
    }

    @Test
    fun stallOnlyWhenFramesFlowOnStaticScreen() {
        val last = t0 + 10_000
        val late = last + BlackScreenWatchdog.PICTURE_STALL_MS
        assertEquals(Verdict.PICTURE_STALL, check(late, first = t0 + 3_000, last = last))
        // passthrough без своего IDR: AA молчит на статичном экране, это не обрыв
        assertNull(check(late, first = t0 + 3_000, last = last, staticFrames = false))
    }

    @Test
    fun flowingPictureIsFine() {
        assertNull(check(t0 + 600_000, first = t0 + 3_000, last = t0 + 599_900))
    }

    @Test
    fun reasonCarriesPrefix() {
        for (v in Verdict.entries) assertTrue(v.reason.startsWith(BlackScreenWatchdog.REASON_PREFIX))
    }

    @Test
    fun headUnitNeverSentStartIsBlackEvenWithFramesFlowing() {
        // лог Levin 2026-11-17: свой старт через 1,5 с, 29 fps в ГУ, а START от ГУ нет - экран чёрный
        val selfStart = t0 + 1_500
        val streaming = t0 + BlackScreenWatchdog.NO_HU_START_MS - 1
        assertNull(check(streaming, start = selfStart, first = selfStart + 700, last = streaming, huStart = 0))
        assertEquals(Verdict.NO_HU_START,
            check(t0 + BlackScreenWatchdog.NO_HU_START_MS, start = selfStart, first = selfStart + 700, last = t0 + 4_990, huStart = 0))
    }

    @Test
    fun headUnitStartKeepsWorkingSessionAlive() {
        val huStart = t0 + 1_600
        assertNull(check(t0 + 600_000, start = huStart, first = huStart + 300, last = t0 + 599_900, huStart = huStart))
    }

    @Test
    fun retriesEveryFiveSecondsForever() {
        for (n in listOf(1, 2, 3, 10, 500)) assertEquals(5_000L, BlackScreenWatchdog.rearmDelayMs(n))
        assertEquals(1_500L, BlackScreenWatchdog.rearmDelayMs(1, Verdict.NO_HU_START.reason))
    }
}
