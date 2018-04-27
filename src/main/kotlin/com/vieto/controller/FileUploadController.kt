package com.vieto.controller

import com.vieto.controller.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.util.Base64Utils
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

@Controller
@RequestMapping("/file")
class FileUploadController @Autowired constructor(private val storageService: StorageService) {
    private val logger = LoggerFactory.getLogger(FileUploadController::class.java)

    @PostMapping("")
    fun handleFileUpload(@RequestParam("file") file: MultipartFile): ResponseEntity<Any> {
        return try {
            // save to storage
            val tempFileName = "${file.originalFilename?.substring(0,3)?:"emp"}_${System.currentTimeMillis()}"
            val uploadFile = storageService.store(file, tempFileName)
            // find file hash
            val fileHash = getFileHash(uploadFile)
            // rename
            if (uploadFile.renameTo(File(uploadFile.parent, fileHash)) == false) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("rename failed")
            }
            logger.debug("[handleFileUpload] saved to ${uploadFile.parent}/$fileHash")
            // return
            val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{fileName}")
                    .buildAndExpand(fileHash).toUri()
            ResponseEntity.created(location).build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.message)
        }
    }

    @Throws(NoSuchAlgorithmException::class)
    fun getFileHash(file: File): String {
        if (file.exists() == false || file.isDirectory) {
            return ""
        }

        val buffer = ByteArray(256)
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use {
            var count = 0
            while (kotlin.run { count = it.read(buffer); count } > 0) {
                digest.update(buffer, 0, count)
            }
        }
        return Base64Utils.encodeToUrlSafeString(digest.digest())
    }
}