package com.crawling.planet.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.crawling.planet"])
class CrawlingPlanetApplication

fun main(args: Array<String>) {
    runApplication<CrawlingPlanetApplication>(*args)
}

