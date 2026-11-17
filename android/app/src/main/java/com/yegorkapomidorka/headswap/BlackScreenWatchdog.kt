package com.yegorkapomidorka.headswap

/**
 * Воздух: сессия есть (6/6 сокетов, CarLife идёт), а на экране ГУ черно. Решение «рвать и подключаться заново» -
 * чистая функция от времён, чтобы покрыть тестами. Закрытие сессии с причиной [REASON_PREFIX] BridgeService
 * превращает в новое ожидание машины (rearm), AA при этом держится тёплой (BridgeHost, воздушный grace).
 *
 * Не срабатывает на PAUSE (ГУ в своём меню - картинки и не должно быть) и после [Verdict] один раз за сессию.
 */
object BlackScreenWatchdog {
    const val REASON_PREFIX = "watchdog"

    /** От ГУ ни байта: сокеты открыты, а CarLife на ГУ не начался. */
    const val NO_HU_BYTES_MS = 10_000L
    /** После START (или возврата с PAUSE) ни одного кадра AA. */
    const val NO_PICTURE_MS = 10_000L
    /** Картинка шла и пропала при живом START. Только если кадры идут и на статичном экране (перекодирование или свой IDR). */
    const val PICTURE_STALL_MS = 30_000L

    enum class Verdict(val why: String) {
        NO_HU_BYTES("ГУ молчит"),
        NO_PICTURE("нет картинки после START"),
        PICTURE_STALL("картинка пропала"),
        ;

        val reason: String get() = "$REASON_PREFIX: $why"
    }

    /**
     * @param sessionStartMs когда открылась сессия (elapsedRealtime)
     * @param huBytes сколько байт пришло от ГУ
     * @param lastStartMs последний START от ГУ (или свой старт), 0 - не было
     * @param paused ГУ прислала PAUSE и START ещё не вернулся
     * @param firstPictureMs первый кадр AA, ушедший в ГУ, 0 - не было
     * @param lastPictureMs последний кадр AA, ушедший в ГУ
     * @param framesOnStaticScreen кадры идут и без движения на экране (перекодирование повторяет кадр, свой IDR раз в 2 с)
     */
    fun check(
        nowMs: Long,
        sessionStartMs: Long,
        huBytes: Long,
        lastStartMs: Long,
        paused: Boolean,
        firstPictureMs: Long,
        lastPictureMs: Long,
        framesOnStaticScreen: Boolean,
    ): Verdict? {
        if (huBytes == 0L) return if (nowMs - sessionStartMs >= NO_HU_BYTES_MS) Verdict.NO_HU_BYTES else null
        if (paused || lastStartMs == 0L) return null
        if (firstPictureMs == 0L) return if (nowMs - lastStartMs >= NO_PICTURE_MS) Verdict.NO_PICTURE else null
        if (!framesOnStaticScreen) return null
        val since = maxOf(lastPictureMs, lastStartMs)
        return if (nowMs - since >= PICTURE_STALL_MS) Verdict.PICTURE_STALL else null
    }

    /** Пауза перед новой попыткой после срабатывания: всегда [RETRY_MS], попытки не кончаются. */
    const val RETRY_MS = 5_000L

    @Suppress("UNUSED_PARAMETER")
    fun rearmDelayMs(consecutive: Int): Long = RETRY_MS
}
