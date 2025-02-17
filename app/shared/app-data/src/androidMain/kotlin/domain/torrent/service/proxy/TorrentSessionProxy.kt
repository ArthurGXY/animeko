/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import android.os.DeadObjectException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemoteTorrentSession
import me.him188.ani.app.domain.torrent.callback.ITorrentSessionStatsCallback
import me.him188.ani.app.domain.torrent.client.ConnectivityAware
import me.him188.ani.app.domain.torrent.cont.ContTorrentSessionGetFiles
import me.him188.ani.app.domain.torrent.parcel.PPeerInfo
import me.him188.ani.app.domain.torrent.parcel.PTorrentSessionStats
import me.him188.ani.app.domain.torrent.parcel.toRemoteContinuationException
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.utils.coroutines.CancellationException
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

class TorrentSessionProxy(
    private val delegate: TorrentSession,
    private val connectivityAware: ConnectivityAware,
    context: CoroutineContext
) : IRemoteTorrentSession.Stub() {
    private val scope = context.childScope()
    private val logger = logger<TorrentSessionProxy>()
    
    override fun getSessionStats(flow: ITorrentSessionStatsCallback?): IDisposableHandle {
        val job = scope.launch(Dispatchers.IO_) { 
            delegate.sessionStats.collect {
                if (!connectivityAware.isConnected) return@collect
                if (it == null) return@collect

                try {
                    flow?.onEmit(
                        PTorrentSessionStats(
                            it.totalSizeRequested,
                            it.downloadedBytes,
                            it.downloadSpeed,
                            it.uploadedBytes,
                            it.uploadSpeed,
                            it.downloadProgress,
                        ),
                    )
                } catch (doe: DeadObjectException) {
                    throw CancellationException("Cancelled collecting session stats.", doe)
                }
            }
        }
        
        return DisposableHandleProxy { job.cancel() }
    }

    override fun getName(): String {
        return runBlocking { delegate.getName() }
    }

    override fun getFiles(cont: ContTorrentSessionGetFiles?): IDisposableHandle? {
        if (cont == null) return null

        val job = scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                if (!connectivityAware.isConnected) return@CoroutineExceptionHandler
                cont.resumeWithException(throwable.toRemoteContinuationException())
            } + Dispatchers.IO_,
        ) {
            val result = delegate.getFiles()
            if (!connectivityAware.isConnected) return@launch
            cont.resume(
                TorrentFileEntryListProxy(result, connectivityAware, scope.coroutineContext),
            )
        }

        return DisposableHandleProxy { job.cancel() }
    }

    override fun getPeers(): Array<PPeerInfo> {
        return delegate.getPeers().map {
            PPeerInfo(
                it.handle,
                it.id,
                it.client,
                it.ipAddr,
                it.ipPort,
                it.progress,
                it.totalDownload.inBytes,
                it.totalUpload.inBytes,
                it.flags,
            )
        }.toTypedArray()
    }

    override fun close() {
        scope.launch {
            delegate.close()
        }
    }

    override fun closeIfNotInUse() {
        scope.launch {
            delegate.closeIfNotInUse()
        }
    }
}