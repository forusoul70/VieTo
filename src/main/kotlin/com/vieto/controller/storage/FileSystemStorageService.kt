package com.vieto.controller.storage

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.util.FileSystemUtils
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
class FileSystemStorageService @Autowired constructor(properties: StorageProperties): StorageService {
    val rootLocation: Path = Paths.get(properties.location)

    override fun initialize() {
        try {
            Files.createDirectories(rootLocation)
        } catch (e: IOException) {
            throw StorageException("Could not initialize storage :  $rootLocation")
        }
    }

    override fun store(file: MultipartFile, fileName: String): File {
        try {
            if (file.isEmpty) {
                throw StorageException("Failed to store empty file $fileName")
            }
            if (fileName.contains("..")) {
                // This is a security check
                throw StorageException("Cannot store file with relative path outside current directory $fileName")
            }
            file.inputStream.use {
                Files.copy(it, rootLocation.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
            }
            return rootLocation.resolve(fileName).toFile()
        } catch (e: IOException) {
            throw StorageException("Failed to store file $fileName", e)
        }
    }

    override fun load(fileName: String): Path {
        return rootLocation.resolve(fileName)
    }

    override fun deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile())
    }

    override fun removeRootPathIfExist(path: Path): Path {
        return rootLocation.relativize(path)
    }

    override fun removeFolder(folder: String) {
        val target = load(folder).toFile()
        if (target.exists() == false) {
            return
        }
        if (target.isDirectory == false) {
            throw StorageException("Target is not folder")
        }
        target.deleteRecursively()
    }
}
