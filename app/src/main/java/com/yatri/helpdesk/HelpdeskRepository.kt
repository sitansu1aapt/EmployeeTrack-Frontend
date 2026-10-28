package com.yatri.helpdesk

class HelpdeskRepository(private val api: HelpdeskApi) {
    suspend fun uploadAttachment(part: okhttp3.MultipartBody.Part) = api.uploadAttachment(part)
    suspend fun createRequest(body: CreateHelpdeskRequestBody) = api.createRequest(body)
    suspend fun getRequests(status: String? = null, queryType: String? = null, limit: Int = 20, offset: Int = 0) = api.getRequests(status, queryType, limit, offset)
    suspend fun getRequestDetail(requestId: Long) = api.getRequestDetail(requestId)
}
