package com.rassvet.essential.data.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.rassvet.essential.data.network.NetworkMonitor


object GgufDownloadPolicy {
    enum class BlockReason {
        CellularWithoutConsent,
        InsufficientStorage,
        NoNetwork,
    }

    data class CheckResult(
        val allowed: Boolean,
        val reason: BlockReason? = null,
        val warningRamMb: Int? = null,
    )

    fun check(
        context: Context,
        preset: LocalModelCatalog.Preset,
        allowCellular: Boolean,
    ): CheckResult {
        if (!NetworkMonitor(context.applicationContext).isOnline()) {
            return CheckResult(false, BlockReason.NoNetwork, preset.ramMb)
        }
        if (!allowCellular && isCellular(context)) {
            return CheckResult(false, BlockReason.CellularWithoutConsent, preset.ramMb)
        }
        val freeMb = context.filesDir.freeSpace / (1024 * 1024)
        val needMb = (preset.expectedBytes ?: 400L * 1024 * 1024) / (1024 * 1024) + 100
        if (freeMb < needMb) {
            return CheckResult(false, BlockReason.InsufficientStorage, preset.ramMb)
        }
        return CheckResult(true, warningRamMb = preset.ramMb)
    }

    fun isCellular(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}


