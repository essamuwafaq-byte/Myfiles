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
    /**
     * ГУ на связи, а сама VIDEO_ENCODER_START так и не прислала: CarLife на ГУ не вышел на экран, мост стартовал видео
     * сам (START_FALLBACK) и шлёт кадры в никуда - экран чёрный. Toyota Levin HSAE ch 20452114 (лог 2026-11-17): в рабочих
     * сессиях START приходит через 1-1,6 с после INIT, в чёрной не пришёл за 2 мин при 29 fps в ГУ.
     */
    // замер по 10 сессиям 2026-11-17/18: START приходит через 0,15-1,6 с после INIT_DONE (INIT_DONE ~0,65 с от старта сессии).
    // 12 с давали ~20 с чёрного экрана (лог 18:22), 5 с - запас в 2 раза к худшему
    // 5 с отрезали рабочее подключение (лог 18:55: ГУ показала CarLife позже), по просьбе владельца - 15 с
    const val NO_HU_START_MS = 15_000L
    /** Пока ГУ не прислала START, напоминаем ей FOREGROUND с таким шагом. */
    const val FOREGROUND_NUDGE_MS = 3_000L
    /** После START (или возврата с PAUSE) ни одного кадра AA. */
    const val NO_PICTURE_MS = 10_000L
    /** Картинка шла и пропала при живом START. Только если кадры идут и на статичном экране (перекодирование или свой IDR). */
    const val PICTURE_STALL_MS = 30_000L

    enum class Verdict(val why: String) {
        NO_HU_BYTES("ГУ молчит"),
        NO_HU_START("ГУ не вывела CarLife на экран (нет START от ГУ)"),
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
     * @param huStartMs когда ГУ сама прислала VIDEO_ENCODER_START (не свой старт), 0 - ни разу за сессию
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
        huStartMs: Long = lastStartMs,
    ): Verdict? {
        if (huBytes == 0L) return if (nowMs - sessionStartMs >= NO_HU_BYTES_MS) Verdict.NO_HU_BYTES else null
        if (huStartMs == 0L) return if (nowMs - sessionStartMs >= NO_HU_START_MS) Verdict.NO_HU_START else null
        if (paused || lastStartMs == 0L) return null
        if (firstPictureMs == 0L) return if (nowMs - lastStartMs >= NO_PICTURE_MS) Verdict.NO_PICTURE else null
        if (!framesOnStaticScreen) return null
        val since = maxOf(lastPictureMs, lastStartMs)
        return if (nowMs - since >= PICTURE_STALL_MS) Verdict.PICTURE_STALL else null
    }

    /** Пауза перед новой попыткой после срабатывания: всегда [RETRY_MS], попытки не кончаются. */
    const val RETRY_MS = 5_000L

    /** ГУ не вывела CarLife: она уже на связи и переподключается сама за ~1,5 с, ждём ГУ заново почти сразу. */
    const val QUICK_RETRY_MS = 1_500L

    @Suppress("UNUSED_PARAMETER")
    fun rearmDelayMs(consecutive: Int, reason: String = ""): Long =
        if (reason == Verdict.NO_HU_START.reason) QUICK_RETRY_MS else RETRY_MS
}
