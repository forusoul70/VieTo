package com.vieto.database

import com.vieto.model.TorrentModel
import org.springframework.data.mongodb.repository.MongoRepository

interface TorrentRepository: MongoRepository<TorrentModel, String> {
    fun findByHash(hash: String): TorrentModel?
}