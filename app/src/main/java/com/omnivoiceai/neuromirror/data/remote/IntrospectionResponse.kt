package com.omnivoiceai.neuromirror.data.remote

import kotlinx.serialization.Serializable

abstract class IntrospectionResponse

@Serializable
data class QuestionIntrospectionResponse(
    val body: String,
    val questions: List<QuestionData>
) : IntrospectionResponse()

@Serializable
data class ChatIntrospectionResponse(
    val body: String,
    val sources: List<String> = emptyList()
) : IntrospectionResponse()