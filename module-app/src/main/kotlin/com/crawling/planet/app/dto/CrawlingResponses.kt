package com.crawling.planet.app.dto

data class StatusResponse(val totalCompanies: Long, val totalReviews: Long)
data class CrawlResponse(val success: Boolean, val message: String, val companiesSaved: Int, val reviewsSaved: Int)
