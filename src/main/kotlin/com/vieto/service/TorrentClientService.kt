package com.vieto.service

import com.turn.ttorrent.client.Client
import com.turn.ttorrent.client.SharedTorrent
import com.turn.ttorrent.common.Torrent
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
import org.springframework.util.StringUtils
import java.io.File
import java.net.InetAddress
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Service
class TorrentClientService constructor(@Autowired val torrentRepository: TorrentRepository,
                                       @Autowired val storageService: FileSystemStorageService): Observer {

    companion object {
        const val TORRENT_CLIENT_POOL_COUNT = 2
    }

    private val clientSemaphore = Semaphore(TORRENT_CLIENT_POOL_COUNT)
    private val currentDownloadMap = HashMap<String, Client>()
    private val downloadProgressCache = HashMap<String, Float>()
    private val logger = LoggerFactory.getLogger(TorrentClientService::class.java)

    fun getList():List<TorrentModel> = torrentRepository.findAll()

    fun addTorrentFile(fileUri: String): ResponseEntity<Any> {
        val hash = StringUtils.getFilename(fileUri) ?: return ResponseEntity.badRequest().body("Invalid file uri, $fileUri")
        val file = storageService.load(hash).toFile()
        try {
            if (file.exists() == false || file.isDirectory) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to load file")
            }
            val torrentRequest = Torrent.load(file)
            val status = requestDownload(torrentRequest)
            if (status.is2xxSuccessful) {
                var torrentModel = TorrentModel(torrentRequest.name, torrentRequest.filenames, torrentRequest.hexInfoHash,
                        torrentRequest.size, fileUri, torrentRequest.comment, torrentRequest.createdBy)
                torrentModel = torrentRepository.insert(torrentModel)
                torrentModel.status = Status.Downloading
                torrentRepository.save(torrentModel)
            }
            return ResponseEntity.status(status).build()
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.message)
        }
    }

    fun requestDownload(torrentRequest: Torrent): HttpStatus {
        if (clientSemaphore.tryAcquire(1, TimeUnit.SECONDS) == false) {
            return HttpStatus.TOO_MANY_REQUESTS
        }

        synchronized(currentDownloadMap) {
            if (currentDownloadMap.containsKey(torrentRequest.hexInfoHash)) {
                return HttpStatus.BAD_REQUEST
            }

            // destination folder
            val destinationFolder = File(storageService.rootLocation.toFile(), torrentRequest.hexInfoHash)
            if (destinationFolder.exists() == false) {
                destinationFolder.mkdir()
            }
            val client = Client(InetAddress.getLocalHost(), SharedTorrent(torrentRequest, destinationFolder))
            client.setMaxDownloadRate(50.0)
            client.setMaxUploadRate(50.0)
            client.download()
            client.addObserver(this)
            currentDownloadMap[torrentRequest.hexInfoHash] = client
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
                it.deleteObserver(this)
                it.stop()
                clientSemaphore.release()
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

    override fun update(o: Observable?, arg: Any?) {
        val c = o as Client
        val hash = c.torrent.hexInfoHash

        synchronized(downloadProgressCache) {
            val progress = c.torrent.completion
            downloadProgressCache[hash] = progress
        }

        if (c.state == Client.ClientState.DONE ||
                c.state == Client.ClientState.SEEDING ||
                c.state == Client.ClientState.ERROR) {
            synchronized(currentDownloadMap) {
                currentDownloadMap.remove(hash)
            }
            c.stop()
            clientSemaphore.release()

            // update db
            val torrentModel = torrentRepository.findByHash(hash)
            if (torrentModel == null) {
                logger.warn("[requestDownload] Failed to find torrent model by hash. name: $hash hash: ${c.torrent.name}")
                return
            }
            torrentModel.status = when (c.state) {
                Client.ClientState.DONE, Client.ClientState.SEEDING  -> Status.Success
                Client.ClientState.ERROR -> Status.Failure
                else -> torrentModel.status
            }
            torrentRepository.save(torrentModel)
            downloadProgressCache.remove(hash)
        }
    }
}