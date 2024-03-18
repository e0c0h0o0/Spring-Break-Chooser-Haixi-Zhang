package sakura.springbreakchooser


import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TranslateService(sourceLanguage: String, destinationLanguage: String) {

    lateinit var options: TranslatorOptions
    lateinit var client: Translator
    var flagTranslatorReady = false
    var flagTranslatorInitializerError = false

    fun fromBCP47(bcp47Code: String): String {
        return bcp47Code.split("-")[0]
    }

    init {
        options = TranslatorOptions.Builder()
            .setSourceLanguage(fromBCP47(sourceLanguage))
            .setTargetLanguage(fromBCP47(destinationLanguage))
            .build()
        client = Translation.getClient(options)
        var conditions = DownloadConditions.Builder()
            .build()
        client.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                flagTranslatorReady = true
            }
            .addOnFailureListener {
                flagTranslatorInitializerError = true
            }

    }

    public fun translate(content: String, callback: (isSuccess: Boolean, translatedContent: String) -> Unit) {
        if(flagTranslatorReady) {
            client.translate(content)
                .addOnSuccessListener {
                    callback(true, it)
                }
                .addOnFailureListener {
                    callback(false, "" + it.toString())
                }
        }
        else {
            callback(false, "Translator not ready yet")
        }
    }
}