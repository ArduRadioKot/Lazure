package com.rassvet.essential.data.llm

import android.content.Context
import com.rassvet.essential.data.local.VaultPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File


class HybridLocalLlmEngine(
    private val context: Context,
    private val prefs: VaultPreferencesRepository,
    private val stub: StubLocalLlmEngine = StubLocalLlmEngine(),
) : LocalLlmEngine {

    override suspend fun complete(request: LocalLlmRequest): String {
        val sb = StringBuilder()
        streamComplete(request).collect { sb.append(it) }
        return sb.toString()
    }

    override fun streamComplete(request: LocalLlmRequest): Flow<String> =
        flow {
            val modelId = request.modelId
            val ramMb = LlamaRuntimeTuning.deviceRamMb(context)
            LocalLlmLog.i(
                "streamComplete model=$modelId session=${request.chatSessionKey} " +
                    "userLen=${request.userMessage.length} vaultRaw=${request.vaultContext.length} " +
                    "webRaw=${request.webContext.length}",
            )
            if (modelId.isBlank()) {
                emit("Выберите модель: кнопка с иконкой хранилища справа вверху в чате.")
                return@flow
            }
            if (!LocalModelFormats.isLocalModelFileName(modelId)) {
                emit("Модель «$modelId» не поддерживается. Скачайте .litertlm из списка моделей.")
                return@flow
            }
            val file = File(context.filesDir, "llm_models/$modelId")
            if (!file.isFile) {
                LocalLlmLog.w("Local model missing: ${file.absolutePath}")
                emit("Модель «$modelId» не найдена. Скачайте её в списке справа или обновите выбор.")
                return@flow
            }
            LocalLlmLog.i("model file size=${file.length() / (1024 * 1024)} MiB path=${file.name}")

            if (!LiteRtLmCapabilities.isAvailable) {
                stub.streamComplete(request.copy(modelId = "stub")).collect { emit(it) }
                return@flow
            }

            val params = LlamaRuntimeTuning.tune(prefs.getLlmRuntimeParams(), modelId, ramMb)
            val computeBackend = prefs.resolveLlmComputeBackend()
            val systemInstruction =
                buildSystemInstruction(
                    modelId = modelId,
                    systemOverlay = request.systemOverlay,
                    hasWebContext = request.webContext.isNotBlank(),
                )
            val userText =
                buildUserMessage(
                    userMessage = request.userMessage,
                    vaultContext = request.vaultContext,
                    webContext = request.webContext,
                    modelId = modelId,
                    ramMb = ramMb,
                )
            val history =
                request.history.map { (isUser, text) ->
                    LocalChatTurn(isUser = isUser, text = text)
                }

            LocalLlmGenerationMetrics.setLoadingWeights()
            val sessionResult =
                LiteRtLmRunner.ensureSession(
                    context = context,
                    modelPath = file.absolutePath,
                    modelId = modelId,
                    computeBackend = computeBackend,
                    params = params,
                    systemInstruction = systemInstruction,
                    chatSessionKey = request.chatSessionKey,
                    history = history,
                )
            if (sessionResult.isFailure) {
                val err = sessionResult.exceptionOrNull()?.message ?: "ошибка LiteRT-LM"
                emit(
                    "Не удалось загрузить «$modelId» ($err). " +
                        "Закройте другие приложения или выберите модель меньше.\n\n",
                )
                stub.streamComplete(request.copy(modelId = "stub")).collect { emit(it) }
                return@flow
            }

            val session = sessionResult.getOrThrow()
            LocalLlmLog.i("LiteRT-LM session ready backend=${session.computeBackend}")
            LocalLlmGenerationMetrics.setPrefill()

            try {
                var gotAny = false
                LiteRtLmRunner.streamUserMessage(
                    session = session,
                    userText = userText,
                    onTokenCount = { count -> LocalLlmGenerationMetrics.onNativeTokenCount(count) },
                ).collect { chunk ->
                    gotAny = true
                    emit(chunk)
                }
                if (!gotAny) {
                    LocalLlmLog.w("LiteRT-LM stream empty")
                    emit("Модель не начала ответ. Перезапустите чат или смените модель.\n\n")
                }
            } catch (t: Throwable) {
                LocalLlmLog.e("LiteRT-LM stream failed", t)
                emit(t.message ?: "ошибка LiteRT-LM")
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun releaseModel() {
        LiteRtLmRunner.releaseSession("explicit release")
    }


    suspend fun warmupModel(
        modelId: String,
        chatSessionKey: Long = 0L,
        systemOverlay: String = "",
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            if (modelId.isBlank() || !LocalModelFormats.isLocalModelFileName(modelId)) {
                return@withContext Result.failure(IllegalArgumentException("unsupported model"))
            }
            val file = File(context.filesDir, "llm_models/$modelId")
            if (!file.isFile) {
                return@withContext Result.failure(IllegalStateException("model file missing"))
            }
            if (!LiteRtLmCapabilities.isAvailable) {
                return@withContext Result.success(Unit)
            }
            val ramMb = LlamaRuntimeTuning.deviceRamMb(context)
            val params = LlamaRuntimeTuning.tune(prefs.getLlmRuntimeParams(), modelId, ramMb)
            val computeBackend = prefs.resolveLlmComputeBackend()
            val systemInstruction =
                buildSystemInstruction(
                    modelId = modelId,
                    systemOverlay = systemOverlay,
                    hasWebContext = false,
                )
            LocalLlmGenerationMetrics.onWarmupStart()
            try {
                LiteRtLmRunner.ensureSession(
                    context = context,
                    modelPath = file.absolutePath,
                    modelId = modelId,
                    computeBackend = computeBackend,
                    params = params,
                    systemInstruction = systemInstruction,
                    chatSessionKey = chatSessionKey,
                    history = emptyList(),
                ).map { Unit }
            } finally {
                LocalLlmGenerationMetrics.onWarmupEnd()
            }
        }

    private fun buildSystemInstruction(modelId: String, systemOverlay: String, hasWebContext: Boolean): String =
        buildString {
            append("Ты — ассистент Lazure. Отвечай кратко по существу.")
            if (hasWebContext) {
                append(
                    " В сообщении пользователя есть «Справка из интернета» — используй её как источник фактов " +
                        "и не отказывайся от ответа из‑за «отсутствия доступа к сети».",
                )
            }
            if (systemOverlay.isNotBlank()) {
                appendLine()
                appendLine()
                append(systemOverlay.trim().take(LlamaRuntimeTuning.systemOverlayCharCap(modelId)))
            }
        }

    private fun buildUserMessage(
        userMessage: String,
        vaultContext: String,
        webContext: String,
        modelId: String,
        ramMb: Long,
    ): String =
        buildString {
            if (webContext.isNotBlank()) {
                appendLine("Справка из интернета:")
                appendLine(webContext.take(LlamaRuntimeTuning.webCharsForModel(modelId, ramMb)))
                appendLine()
            }
            if (vaultContext.isNotBlank()) {
                appendLine("Справка из хранилища:")
                appendLine(vaultContext.take(LlamaRuntimeTuning.vaultCharsForModel(modelId, ramMb)))
                appendLine()
            }
            append(userMessage.trim())
        }.let { raw ->
            val cap = LlamaRuntimeTuning.promptCharCap(modelId, ramMb)
            if (raw.length <= cap) raw else raw.take(cap)
        }
}


