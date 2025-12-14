package com.crawling.planet.app

import com.crawling.planet.httpclient.annotation.EnableWebClients
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.crawling.planet"])
@ConfigurationPropertiesScan(basePackages = ["com.crawling.planet"])
@EnableJpaRepositories(basePackages = ["com.crawling.planet.domain.repository"])
@EntityScan(basePackages = ["com.crawling.planet.domain.entity"])
@EnableWebClients(basePackages = ["com.crawling.planet.crawler.client"])
class CrawlingPlanetApplication

fun main(args: Array<String>) {
    runApplication<CrawlingPlanetApplication>(*args)
}

