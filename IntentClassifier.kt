package com.kate.assistant.features.nlp

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

class IntentClassifier(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = context.assets.open("model_intent.tflite").readBytes()
        interpreter = Interpreter(ByteBuffer.wrap(model))
    }

    fun classify(input: FloatArray): Int {
        val output = Array(1) { FloatArray(6) } // adjust to your intents
        interpreter.run(arrayOf(input), output)

        return output[0].indices.maxByOrNull { output[0][it] } ?: 0
    }
}