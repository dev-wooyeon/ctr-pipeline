package com.example.ctr.domain.model

data class ParsingResult<T>(
        val result: T? = null,
        val rawData: ByteArray? = null,
        val errorMessage: String? = null,
        val stackTrace: String? = null
) {
    fun isSuccess(): Boolean = result != null && errorMessage == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsingResult<*>

        if (result != other.result) return false
        if (rawData != null) {
            if (other.rawData == null) return false
            if (!rawData.contentEquals(other.rawData)) return false
        } else if (other.rawData != null) return false
        if (errorMessage != other.errorMessage) return false
        if (stackTrace != other.stackTrace) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = result?.hashCode() ?: 0
        result1 = 31 * result1 + (rawData?.contentHashCode() ?: 0)
        result1 = 31 * result1 + (errorMessage?.hashCode() ?: 0)
        result1 = 31 * result1 + (stackTrace?.hashCode() ?: 0)
        return result1
    }
}
