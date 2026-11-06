package com.rassvet.essential.data.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.rassvet.essential.litert.LiteRtNativeBootstrap
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LiteRtLmInstance(
    val engine: Engine,
    var conversation: Conversation,
    val modelId: String,
    val computeBackend: String,
    val chatSessionKey: Long,
    val systemInstruction: String,
)


@OptIn(ExperimentalApi::class)
internal object LiteRtLmRunner {
    private val mutex = Mutex()
    private var instance: LiteRtLmInstance? = null

    suspend fun ensureSession(
        context: Context,
        modelPath: String,
        modelId: String,
        computeBackend: String,
        params: LlamaRuntimeParams,
        systemInstruction: String,
        chatSessionKey: Long,
        history: List<LocalChatTurn> = emptyList(),
    ): Result<LiteRtLmInstance> =
        mutex.withLock {
            val current = instance
            if (
                current != null &&
                    current.modelId == modelId &&
                    current.computeBackend == computeBackend &&
                    current.chatSessionKey == chatSessionKey &&
                    current.systemInstruction == systemInstruction
            ) {
                return@withLock Result.success(current)
            }

            if (
                current != null &&
                    current.modelId == modelId &&
                    current.computeBackend == computeBackend
            ) {
                resetConversationLocked(current, params, systemInstruction, history)
                instance =
                    current.copy(
                        chatSessionKey = chatSessionKey,
                        systemInstruction = systemInstruction,
                    )
                return@withLock Result.success(instance!!)
            }

            releaseLocked("model/backend switch")
            val loaded =
                loadInstance(
                    context = context,
                    modelPath = modelPath,
                    modelId = modelId,
                    computeBackend = computeBackend,
                    params = params,
                    systemInstruction = systemInstruction,
                    chatSessionKey = chatSessionKey,
                    history = history,
                )
            if (loaded.isFailure && LlmComputeBackend.isGpu(computeBackend)) {
                LocalLlmLog.w("LiteRT-LM GPU init failed — retry CPU")
                return@withLock loadInstance(
                    context = context,
                    modelPath = modelPath,
                    modelId = modelId,
                    computeBackend = LlmComputeBackend.CPU,
                    params = params,
                    systemInstruction = systemInstruction,
                    chatSessionKey = chatSessionKey,
                    history = history,
                )
            }
            loaded
        }

    fun streamUserMessage(
        session: LiteRtLmInstance,
        userText: String,
        onTokenCount: ((Int) -> Unit)? = null,
    ): Flow<String> =
        callbackFlow {
            var tokens = 0
            session.conversation.sendMessageAsync(
                Contents.of(Content.Text(userText)),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val chunk = message.toString()
                        if (chunk.isNotEmpty()) {
                            tokens++
                            onTokenCount?.invoke(tokens)
                            trySend(chunk)
                        }
                    }

                    override fun onDone() {
                        close()
                    }

                    override fun onError(throwable: Throwable) {
                        if (throwable is CancellationException) {
                            close()
                        } else {
                            close(throwable)
                        }
                    }
                },
            )
            awaitClose {}
        }

    suspend fun releaseSession(reason: String = "release") {
        mutex.withLock { releaseLocked(reason) }
    }

    private fun resetConversationLocked(
        current: LiteRtLmInstance,
        params: LlamaRuntimeParams,
        systemInstruction: String,
        history: List<LocalChatTurn>,
    ) {
        LocalLlmLog.i(
            "LiteRT-LM reset conversation model=${current.modelId} " +
                "backend=${current.computeBackend}",
        )
        runCatching { current.conversation.close() }
        current.conversation =
            newConversation(
                engine = current.engine,
                params = params,
                systemInstruction = systemInstruction,
                history = history,
                computeBackend = current.computeBackend,
            )
    }

    private fun releaseLocked(reason: String) {
        val current = instance ?: return
        instance = null
        LocalLlmLog.i("LiteRT-LM release ($reason) model=${current.modelId}")
        runCatching { current.conversation.close() }
        runCatching { current.engine.close() }
    }

    private suspend fun loadInstance(
        context: Context,
        modelPath: String,
        modelId: String,
        computeBackend: String,
        params: LlamaRuntimeParams,
        systemInstruction: String,
        chatSessionKey: Long,
        history: List<LocalChatTurn>,
    ): Result<LiteRtLmInstance> =
        withContext(Dispatchers.Default) {
            runCatching {
                if (LlmComputeBackend.isGpu(computeBackend)) {
                    LiteRtNativeBootstrap.ensureLoaded()
                }

                val speculativeDecoding =
                    if (LlmComputeBackend.isGpu(computeBackend)) {
                        runCatching {
                            Capabilities(modelPath).use { it.hasSpeculativeDecodingSupport() }
                        }.getOrDefault(false)
                    } else {
                        false
                    }

                ExperimentalFlags.enableSpeculativeDecoding = speculativeDecoding
                LocalLlmLog.i(
                    "LiteRT-LM initialize model=$modelId backend=$computeBackend " +
                        "mtp=$speculativeDecoding path=$modelPath",
                )

                val engine =
                    Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = LlmComputeBackend.toBackend(computeBackend),

                            maxNumTokens = params.contextSize,
                            cacheDir = context.cacheDir.absolutePath,
                        ),
                    )
                engine.initialize()
                ExperimentalFlags.enableSpeculativeDecoding = null

                val conversation =
                    newConversation(
                        engine = engine,
                        params = params,
                        systemInstruction = systemInstruction,
                        history = history,
                        computeBackend = computeBackend,
                    )
                LiteRtLmInstance(
                    engine = engine,
                    conversation = conversation,
                    modelId = modelId,
                    computeBackend = computeBackend,
                    chatSessionKey = chatSessionKey,
                    systemInstruction = systemInstruction,
                ).also { instance = it }
            }.onFailure { LocalLlmLog.e("LiteRT-LM load failed", it) }
        }

    private fun newConversation(
        engine: Engine,
        params: LlamaRuntimeParams,
        systemInstruction: String,
        history: List<LocalChatTurn>,
        computeBackend: String,
    ): Conversation {
        val config =
            ConversationConfig(
                systemInstruction =
                    if (systemInstruction.isBlank()) {
                        null
                    } else {
                        Contents.of(systemInstruction.trim())
                    },
                initialMessages = history.map { it.toMessage() },
                samplerConfig =
                    SamplerConfig(
                        topK = params.topK,
                        topP = params.topP.toDouble(),
                        temperature = params.temperature.toDouble(),
                    ),
            )
        return engine.createConversation(config)
    }
}

internal data class LocalChatTurn(val isUser: Boolean, val text: String) {
    fun toMessage(): Message =
        if (isUser) {
            Message.user(text)
        } else {
            Message.model(text)
        }
}


