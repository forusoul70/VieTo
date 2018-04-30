package com.vieto.controller.storage

import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Path

interface StorageService {
    @Throws(StorageException::class)
    fun initialize()
    @Throws(StorageException::class)
    fun store(file: MultipartFile, fileName: String): File
    fun load(fileName: String): Path
    fun deleteAll()
    fun removeRootPathIfExist(path: Path): Path
    @Throws(StorageException::class)
    fun removeFolder(folder: String)
}