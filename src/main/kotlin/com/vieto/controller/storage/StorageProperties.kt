package com.vieto.controller.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("storage")
class StorageProperties {
    val location:String = "/home/lee/Vieto"
}