package org.dpdns.alwaysup.subflow.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

internal fun millisUntilMidnight(clock: Clock, zone: ZoneId): Long {
    val now = clock.instant()
    return Duration.between(now, now.atZone(zone).toLocalDate().plusDays(1)
        .atStartOfDay(zone).toInstant()).toMillis().coerceAtLeast(1L)
}

class CurrentDateObserver internal constructor(
    context: Context,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    constructor(context: Context) : this(context, Clock.systemUTC(), { ZoneId.systemDefault() },
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

    private val app = context.applicationContext
    private val mutableDate = MutableStateFlow(LocalDate.ofInstant(clock.instant(), zone()))
    val currentLocalDate = mutableDate.asStateFlow()
    private var job: Job? = null
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { restart() }
    }
    private fun restart() {
        job?.cancel()
        mutableDate.value = LocalDate.ofInstant(clock.instant(), zone())
        job = scope.launch {
            while (isActive) {
                delay(millisUntilMidnight(clock, zone()))
                mutableDate.value = LocalDate.ofInstant(clock.instant(), zone())
            }
        }
    }
    override fun onResume(owner: LifecycleOwner) {
        if (!registered) {
            ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        restart()
    }
    override fun onPause(owner: LifecycleOwner) { stop() }
    private fun stop() {
        job?.cancel()
        job = null
        if (registered) { app.unregisterReceiver(receiver); registered = false }
    }
    fun close() { stop(); scope.cancel() }
    override fun onDestroy(owner: LifecycleOwner) { close() }
}
