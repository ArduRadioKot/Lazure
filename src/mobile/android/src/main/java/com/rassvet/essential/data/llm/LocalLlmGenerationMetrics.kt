package com.rassvet.essential.data.llm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.roundToInt


object LocalLlmGenerationMetrics {
    enum class Phase {
        Idle,

        Preparing,

        LoadingWeights,

        Prefill,

        Generating,
    }

    data class Snapshot(
        val phase: Phase = Phase.Idle,
        val tokensPerSec: Float? = null,
        val generatedTokens: Int = 0,
    ) {
        val active: Boolean get() = phase != Phase.Idle
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private var startNanos = 0L
    private var smoothedTps = 0f

    fun onStreamStart() {
        startNanos = System.nanoTime()
        smoothedTps = 0f
        _snapshot.value = Snapshot(phase = Phase.Preparing, tokensPerSec = null, generatedTokens = 0)
    }

    fun onWarmupStart() {
        _snapshot.value = Snapshot(phase = Phase.LoadingWeights, tokensPerSec = null, generatedTokens = 0)
    }

    fun onWarmupEnd() {
        _snapshot.update { snap ->
            if (snap.phase == Phase.LoadingWeights && snap.generatedTokens == 0) {
                Snapshot()
            } else {
                snap
            }
        }
    }

    fun setLoadingWeights() {
        _snapshot.update {
            if (it.phase == Phase.Idle) it else it.copy(phase = Phase.LoadingWeights, tokensPerSec = null)
        }
    }

    fun setPrefill() {
        _snapshot.update {
            if (it.phase == Phase.Idle) it else it.copy(phase = Phase.Prefill, tokensPerSec = null)
        }
    }

    fun onChunk(text: String) {
        if (text.isEmpty()) return
        updateGenerating(estimateTokens(_snapshot.value.generatedTokens, text.length))
    }


    fun onNativeTokenCount(count: Int) {
        if (count <= 0) return
        updateGenerating(count)
    }

    private fun updateGenerating(totalTokens: Int) {
        val total = max(1, totalTokens)
        val prev = _snapshot.value
        val elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0
        val tps =
            if (elapsedSec >= 0.08) {
                val instant = (total / elapsedSec).toFloat()
                smoothedTps =
                    if (smoothedTps <= 0f) {
                        instant
                    } else {
                        smoothedTps * 0.65f + instant * 0.35f
                    }
                smoothedTps
            } else {
                prev.tokensPerSec
            }
        _snapshot.update {
            it.copy(
                phase = Phase.Generating,
                generatedTokens = total,
                tokensPerSec = tps,
            )
        }
    }

    private fun estimateTokens(alreadyCounted: Int, newChars: Int): Int {
        val added = max(1, (newChars / 3.5).toInt())
        return alreadyCounted + added
    }

    fun onStreamEnd() {
        _snapshot.value = Snapshot(phase = Phase.Idle, tokensPerSec = null, generatedTokens = 0)
    }


    fun formatProgressLabel(
        snap: Snapshot,
        loadingHint: String,
        weightsLoadingHint: String,
        prefillHint: String,
    ): String? {
        if (!snap.active) return null
        val parts = mutableListOf<String>()
        snap.tokensPerSec?.let { parts.add(formatTokensPerSec(it)) }
        if (snap.generatedTokens > 0) {
            parts.add("(${snap.generatedTokens})")
        }
        if (parts.isNotEmpty()) return parts.joinToString(" ")
        return when (snap.phase) {
            Phase.LoadingWeights -> weightsLoadingHint
            Phase.Prefill -> prefillHint
            Phase.Preparing, Phase.Generating -> loadingHint
            Phase.Idle -> null
        }
    }

    fun formatTokensPerSec(tps: Float): String {
        val v =
            if (tps < 10f) {
                (tps * 10f).roundToInt() / 10f
            } else {
                tps.roundToInt().toFloat()
            }
        return if (v < 10f) {
            String.format("%.1f t/s", v)
        } else {
            String.format("%.0f t/s", v)
        }
    }

}


