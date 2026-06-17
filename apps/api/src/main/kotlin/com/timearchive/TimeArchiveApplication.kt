package com.timearchive

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TimeArchiveApplication

fun main(args: Array<String>) {
    runApplication<TimeArchiveApplication>(*args)
}
