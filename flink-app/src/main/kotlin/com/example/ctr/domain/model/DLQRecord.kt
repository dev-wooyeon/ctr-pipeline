package com.example.ctr.domain.model

import java.time.Instant

data class DLQRecord(
    val source: String,
    val rawTopic: String,
    val errorCode: String,
    val errorMessage: String,
    val rawData: String? = null,
    val eventTimeMillisUtc: Long? = null,
    val stackTrace: String? = null,
    val createdAtUtc: Long = Instant.now().toEpochMilli()
)
