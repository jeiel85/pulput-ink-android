package io.pulpit.ink.data.api

import android.content.Context
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps Play Asset Delivery for the bundled base Whisper model so the onboarding
 * UI can observe its fast-follow download (or trigger it) without touching the
 * Play Core API directly.
 *
 * Note: fast-follow packs are only delivered when the app is installed from
 * Google Play. On sideloaded / debug installs the pack never arrives and the
 * caller should fall back to the Hugging Face download path.
 */
class AssetModelDelivery(private val context: Context) {

    private val TAG = "AssetModelDelivery"
    private val manager by lazy { AssetPackManagerFactory.getInstance(context) }

    enum class Status { PENDING, DOWNLOADING, TRANSFERRING, COMPLETED, FAILED, WAITING_FOR_WIFI, NOT_DELIVERED }

    data class Progress(
        val status: Status,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) {
        val percent: Int
            get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
    }

    /** True if the base model pack is already on disk and ready to use. */
    fun isBaseModelReady(): Boolean {
        return try {
            manager.getPackLocation(WhisperModelManager.BASE_ASSET_PACK)?.assetsPath() != null
        } catch (e: Exception) {
            Log.w(TAG, "getPackLocation failed", e)
            false
        }
    }

    /**
     * Requests the base model pack and emits progress until it completes or fails.
     * The flow closes after a terminal state (COMPLETED / FAILED / NOT_DELIVERED).
     */
    fun observeBaseModelDelivery(): Flow<Progress> = callbackFlow {
        val packName = WhisperModelManager.BASE_ASSET_PACK

        // Already delivered — emit completion and finish.
        if (isBaseModelReady()) {
            trySend(Progress(Status.COMPLETED, 1, 1))
            close()
            return@callbackFlow
        }

        val listener = AssetPackStateUpdateListener { state ->
            if (state.name() != packName) return@AssetPackStateUpdateListener
            when (state.status()) {
                AssetPackStatus.PENDING ->
                    trySend(Progress(Status.PENDING, 0, state.totalBytesToDownload()))
                AssetPackStatus.DOWNLOADING ->
                    trySend(Progress(Status.DOWNLOADING, state.bytesDownloaded(), state.totalBytesToDownload()))
                AssetPackStatus.TRANSFERRING ->
                    trySend(Progress(Status.TRANSFERRING, state.bytesDownloaded(), state.totalBytesToDownload()))
                AssetPackStatus.WAITING_FOR_WIFI ->
                    trySend(Progress(Status.WAITING_FOR_WIFI, state.bytesDownloaded(), state.totalBytesToDownload()))
                AssetPackStatus.COMPLETED -> {
                    trySend(Progress(Status.COMPLETED, state.totalBytesToDownload().coerceAtLeast(1), state.totalBytesToDownload().coerceAtLeast(1)))
                    close()
                }
                AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                    Log.w(TAG, "Asset pack delivery ended without success: status=${state.status()} error=${state.errorCode()}")
                    trySend(Progress(Status.FAILED, state.bytesDownloaded(), state.totalBytesToDownload()))
                    close()
                }
                else -> { /* UNKNOWN / NOT_INSTALLED / REQUIRES_USER_CONFIRMATION — wait for next update */ }
            }
        }

        manager.registerListener(listener)

        manager.fetch(listOf(packName))
            .addOnFailureListener { e ->
                Log.w(TAG, "fetch() failed — pack likely unavailable (sideload?).", e)
                trySend(Progress(Status.NOT_DELIVERED, 0, 0))
                close()
            }

        awaitClose { manager.unregisterListener(listener) }
    }
}
