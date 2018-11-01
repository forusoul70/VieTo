package com.vieto.service

import bt.Bt
import bt.data.file.FileSystemStorage
import bt.dht.DHTConfig
import bt.dht.DHTModule
import bt.magnet.MagnetUri
import bt.magnet.MagnetUriParser
import bt.runtime.BtClient
import bt.runtime.Config
import bt.torrent.TorrentSessionState
import com.vieto.controller.storage.FileSystemStorageService
import com.vieto.controller.storage.StorageException
import com.vieto.database.TorrentRepository
import com.vieto.model.Status
import com.vieto.model.TorrentModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.util.Base64Utils
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Semaphore
import java.util.function.Consumer

@Service
class TorrentClientService constructor(@Autowired val torrentRepository: TorrentRepository,
                                       @Autowired val storageService: FileSystemStorageService) {

    companion object {
        const val TORRENT_CLIENT_POOL_COUNT = 1
    }

    private val requestLock = Semaphore(TORRENT_CLIENT_POOL_COUNT)
    private val currentDownloadMap = HashMap<String, BtClient>()
    private val downloadProgressCache = HashMap<String, Float>()
    private val logger = LoggerFactory.getLogger(TorrentClientService::class.java)

    fun getList():List<TorrentModel> = torrentRepository.findAll()

    fun requestByMagnet(magnet: String): ResponseEntity<Any> {
        logger.info("[requestByMagnet] $magnet")
        try {
            if (requestLock.tryAcquire() == false) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Sever is busy")
            }

            val magnetUri = MagnetUriParser.parser().parse(magnet)
            val status = requestDownload(magnetUri)
            logger.info("[requestByMagnet] $status")
            if (status.is2xxSuccessful) {
                val name = magnetUri.displayName.orElseGet { "" }
                var torrentModel = TorrentModel(name, magnetUri.hash())
                torrentModel = torrentRepository.insert(torrentModel)
                torrentModel.status = Status.Downloading
                torrentRepository.save(torrentModel)
            }
            return ResponseEntity.status(status).build()
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.message)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.message)
        }
    }

    @Throws(IllegalArgumentException::class)
    fun requestDownload(magnetUri: MagnetUri): HttpStatus {
        synchronized(currentDownloadMap) {
            val torrentHash = magnetUri.hash()
            if (currentDownloadMap.containsKey(torrentHash)) {
                return HttpStatus.BAD_REQUEST
            }

            // destination folder
            val destinationFolder = File(storageService.rootLocation.toFile(), torrentHash)
            if (destinationFolder.exists() == false) {
                destinationFolder.mkdir()
            }

            val client = createTorrentClient(destinationFolder, magnetUri)
            val future = client.startAsync(DownloadListener(torrentHash, this@TorrentClientService), 1000)
            future.whenComplete { _, _ ->
                onFinishSessionState(torrentHash)
            }
            currentDownloadMap[torrentHash] = client
        }
        return HttpStatus.OK
    }

    fun getDownloadProgress(hash: String): Float {
        return synchronized(downloadProgressCache) {
            downloadProgressCache[hash]?:0.0f
        }
    }

    fun deleteByHash(hash: String): ResponseEntity<Any> {
        val torrentModel = torrentRepository.findByHash(hash) ?: return ResponseEntity.badRequest().body("Failed to find torrent model")
        var canceled = false
        synchronized(currentDownloadMap) {
            currentDownloadMap.remove(hash)?.let {
                it.stop()
                canceled = true
            }
            return try {
                storageService.removeFolder(hash)
                torrentRepository.delete(torrentModel)
                logger.info("[deleteByHash] ${torrentModel.name}, cancel ? $canceled")
                ResponseEntity.ok("Success, cancel ? $canceled")
            } catch (e: StorageException) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.message)
            }
        }
    }

    private fun onUpdateTorrentSessionState(hash: String, sessionState: TorrentSessionState) {
        synchronized(downloadProgressCache) {
            downloadProgressCache[hash] = sessionState.piecesComplete.toFloat() / sessionState.piecesTotal.toFloat()
        }
    }

    private fun onFinishSessionState(hash: String) {
        requestLock.release()
        synchronized(currentDownloadMap) {
            currentDownloadMap.remove(hash)
        }

        // update db
        val torrentModel = torrentRepository.findByHash(hash)
        if (torrentModel == null) {
            logger.warn("[onFinishSessionState] Failed to find torrent model by hash. hash: $hash")
            return
        }
        logger.warn("[onFinishSessionState] finish: $hash")
        torrentModel.status = Status.Success
        torrentRepository.save(torrentModel)
        synchronized(downloadProgressCache) {
            downloadProgressCache.remove(hash)
        }
    }

    private fun createTorrentClient(downloadPath: File, magnetUri: MagnetUri): BtClient {
        val config = object : Config() {
            override fun getNumOfHashingThreads(): Int {
                return 1
            }
        }

        // enable bootstrapping from public routers
        val dhtModule = DHTModule(object: DHTConfig() {
            override fun shouldUseRouterBootstrap(): Boolean {
                return true
            }
        })

        // get download directory
        val targetDirectory = downloadPath.toPath()

        // create file system based backend for torrent data
        val storage = FileSystemStorage(targetDirectory)

        // create client with a private runtime
        return Bt.client()
                .config(config)
                .storage(storage)
                .magnet(magnetUri)
                .autoLoadModules()
                .module(dhtModule)
                .stopWhenDownloaded()
                .build()
    }

    private fun MagnetUri.hash(): String {
        return Base64Utils.encodeToString(torrentId.bytes)
    }

    private class DownloadListener(val hash: String, service: TorrentClientService): Consumer<TorrentSessionState> {
        private val logger = LoggerFactory.getLogger(DownloadListener::class.java)
        private val serviceRef = WeakReference(service)

        override fun accept(t: TorrentSessionState) {
            serviceRef.get()?.onUpdateTorrentSessionState(hash, t)
        }
    }
}