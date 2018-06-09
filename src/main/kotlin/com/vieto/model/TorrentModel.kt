package com.vieto.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Field

enum class Status {
    Idle,
    Downloading,
    Success,
    Failure
}

open class TorrentModel(val name: String, val hash: String) {
    @Id var id:String? = null
    var status: Status = Status.Idle
}

class DownloadingTorrentModel(val progress: Float, t: TorrentModel) : TorrentModel(t.name, t.hash) {
    init {
        this.id = t.id
        this.status = t.status
    }
}