package com.vieto.controller


import com.vieto.controller.storage.StorageService
import com.vieto.model.DownloadingTorrentModel
import com.vieto.model.MagnetRequestModel
import com.vieto.model.Status
import com.vieto.model.TorrentModel
import com.vieto.service.TorrentClientService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.util.InvalidMimeTypeException
import org.springframework.util.MimeType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/torrent")
class TorrentRestController @Autowired constructor(@Autowired protected val torrentClientService: TorrentClientService,
                                                   private val storageService: StorageService) {
    @RequestMapping(method = [RequestMethod.GET], value = ["/list"])
    fun getTorrentList(): List<TorrentModel> {
        return torrentClientService.getList().map {
            if (it.status == Status.Downloading) {
                DownloadingTorrentModel(torrentClientService.getDownloadProgress(it.hash), it)
            } else {
                it
            }
        }
    }

    @RequestMapping(method = [RequestMethod.POST])
    fun requestByMagent(@RequestBody magnet: MagnetRequestModel): ResponseEntity<Any> {
        return torrentClientService.requestByMagnet(magnet.magnet)
    }

    @RequestMapping(method = [RequestMethod.GET], value = ["/folder/{hash}"])
    fun queryFolder(@PathVariable hash: String): ResponseEntity<List<FileModel>>  {
        val folderPath = storageService.load(hash)
        val folder = folderPath.toFile()
        if (folder.exists() == false || folder.isDirectory == false) {
            return ResponseEntity.badRequest().body(listOf())
        }

        val fileList = folder.listFiles().map {
            val mimeType = try {
                MimeType.valueOf(it.extension).toString()
            } catch (e: InvalidMimeTypeException) {
                "Invalid"
            }
            FileModel(it.name, it.path, mimeType)
        }

        return ResponseEntity.ok(fileList)
    }

    @RequestMapping(method = [RequestMethod.DELETE], value = ["/{hash}"])
    fun deleteByHash(@PathVariable hash: String): ResponseEntity<Any> {
        return torrentClientService.deleteByHash(hash)
    }
}

class FileModel(val name: String, val path: String, val mimeType: String)