package com.vieto.controller


import com.vieto.model.DownloadingTorrentModel
import com.vieto.model.RequestTorrent
import com.vieto.model.Status
import com.vieto.model.TorrentModel
import com.vieto.service.TorrentClientService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/torrent")
class TorrentRestController @Autowired constructor(@Autowired protected val torrentClientService: TorrentClientService) {
    @RequestMapping(method = [RequestMethod.GET], value = ["/list"])
    fun getList(): List<TorrentModel> {
        return torrentClientService.getList().map {
            if (it.status == Status.Downloading) {
                DownloadingTorrentModel(torrentClientService.getDownloadProgress(it.hash), it)
            } else {
                it
            }
        }
    }

    @RequestMapping(method = [RequestMethod.POST])
    fun add(@RequestBody requestTorrent: RequestTorrent): ResponseEntity<Any> {
        return torrentClientService.addTorrentFile(requestTorrent.fileUri)
    }
}