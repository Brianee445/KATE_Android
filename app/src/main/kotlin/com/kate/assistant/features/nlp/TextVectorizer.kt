package com.kate.assistant.features.nlp

class TextVectorizer {

    private val vocab = mapOf(
        "open" to 1,
        "youtube" to 2,
        "play" to 3,
        "music" to 4,
        "remind" to 5,
        "minutes" to 6
        // expand gradually
    )

    fun vectorize(text: String): IntArray {
        val tokens = text.lowercase().split(" ")
        val result = IntArray(10)

        for (i in tokens.indices.take(10)) {
            result[i] = vocab[tokens[i]] ?: 0
        }

        return result
    }
}
