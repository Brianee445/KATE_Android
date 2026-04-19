package com.kate.assistant.features.nlp

import android.content.Context
import org.json.JSONObject

class LabelMapper(context: Context) {

    private val map: Map<Int, String>

    init {
        val json = context.assets.open("labels.json")
            .bufferedReader()
            .use { it.readText() }

        val obj = JSONObject(json)

        map = obj.keys().asSequence().associate {
            it.toInt() to obj.getString(it)
        }
    }

    fun getLabel(index: Int): String {
        return map[index] ?: "UNKNOWN"
    }
}