package com.manoj.eventclassifier.domain

interface ClassificationUseCase<I, O> {
    suspend fun initialize()
    suspend operator fun invoke(input: I): O
}
