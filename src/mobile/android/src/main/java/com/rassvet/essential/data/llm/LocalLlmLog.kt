package com.rassvet.essential.data.llm

import android.util.Log


internal object LocalLlmLog {
    const val TAG = "EssentialLlm"

    fun i(msg: String) = Log.i(TAG, msg)

    fun w(msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(TAG, msg, tr) else Log.w(TAG, msg)
    }

    fun e(msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(TAG, msg, tr) else Log.e(TAG, msg)
    }

    fun elapsed(label: String, startMs: Long) {
        i("$label: ${System.currentTimeMillis() - startMs} ms")
    }
}


