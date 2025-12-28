package com.k2fsa.sherpa.onnx.tts.engine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

fun getSampleText(lang: String): String {
    var text = ""
    when (lang) {
        "ara" -> {
            text = "هذا هو محرك تحويل النص إلى كلام باستخدام الجيل القادم من كالدي"
        }
        // ...otros idiomas omitidos por brevedad...
        "spa" -> {
            text = "Este es un motor de texto a voz que utiliza kaldi de próxima generación."
        }
        // ...otros idiomas omitidos por brevedad...
    }
    return text
}

class GetSampleText : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var result = TextToSpeech.LANG_AVAILABLE
        val text: String = getSampleText(TtsEngine.lang ?: "")
        if (text.isEmpty()) {
            result = TextToSpeech.LANG_NOT_SUPPORTED
        }

        val intent = Intent().apply {
            if (result == TextToSpeech.LANG_AVAILABLE) {
                putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, text)
            } else {
                putExtra("sampleText", text)
            }
        }
        setResult(result, intent)
        finish()
    }
}
