package com.vieto.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Field

enum class Status {
    Idle,
    Downloading,
    Success,
    Failure
}

open class TorrentModel(val name: String, val files: List<String>, val hash: String, val size: Long, val filePath: String,
                   var comment: String? = null, var createdBy: String? = null) {
    @Id var id:String? = null
    var status: Status = Status.Idle
}

class DownloadingTorrentModel(val progress: Float, t: TorrentModel) : TorrentModel(t.name, t.files, t.hash, t.size, t.filePath, t.comment, t.createdBy) {
    init {
        this.id = t.id
        this.status = t.status
    }
}