package com.vieto

import com.vieto.controller.storage.StorageProperties
import com.vieto.controller.storage.StorageService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties::class)
class Application {
    @Bean
    fun initializeStorage(storageService: StorageService): CommandLineRunner {
        return CommandLineRunner {
            storageService.initialize()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}