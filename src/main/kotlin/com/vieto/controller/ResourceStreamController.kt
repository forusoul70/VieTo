package com.vieto.controller

import com.vieto.controller.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Controller
import org.springframework.util.MimeType
import org.springframework.web.bind.annotation.*
import java.io.*
import java.net.URLDecoder
import java.nio.file.Files
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Controller
@RequestMapping("/stream")
class ResourceStreamController(private val storageService: StorageService) {
    private val logger = LoggerFactory.getLogger(ResourceStreamController::class.java)

    @RequestMapping("**", method = [RequestMethod.GET])
    fun getVideoResource(request: HttpServletRequest, response: HttpServletResponse) {
        val path = URLDecoder.decode(request.requestURI.let { return@let it.substring("/stream/".length) }, "UTF-8")
        val resourcePath = storageService.load(path)
        val resource = resourcePath.toFile()
        if (resource.exists() == false) {
            response.status = HttpStatus.NOT_FOUND.value()
            return
        }
        val contentType = Files.probeContentType(resourcePath)
        if (contentType != "video/mp4") {
            response.status = HttpStatus.NOT_ACCEPTABLE.value()
            resource.outputStream().write( "$contentType is not support".toByteArray())
            return
        }
        try {
            val size = resource.length()
            val requestRange = request.getHeader(HttpHeaders.RANGE)?.let {
                HttpRange.parseRanges(it)[0]
            }
            val start: Long = requestRange?.getRangeStart(0)?: 0L
            val end: Long = requestRange?.getRangeEnd(size) ?: size - 1
            val partSize = end - start + 1

            response.status = if (partSize == size) HttpStatus.OK.value() else HttpStatus.PARTIAL_CONTENT.value()
            response.contentType = contentType.toString()
            response.addHeader(HttpHeaders.CONTENT_RANGE, "bytes $start-$end/$size")
            response.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
            response.addHeader(HttpHeaders.CONTENT_LENGTH, "$partSize")
            response.outputStream.let {
                RandomAccessFile(resource, "r").use { randomAccessFile ->
                    randomAccessFile.seek(start)
                    var remain:Long = partSize
                    val bufferSize:Long = 8 * 1024
                    val buffer = ByteArray(bufferSize.toInt())

                    do {
                        val block = if (remain > bufferSize) bufferSize else remain
                        val readLength = randomAccessFile.read(buffer, 0, block.toInt())
                        it.write(buffer, 0, readLength)
                        remain -= readLength
                    } while (remain > 0)
                }
            }

        } catch (e: Exception) {
            response.status = HttpStatus.NOT_FOUND.value()
        }
    }
}