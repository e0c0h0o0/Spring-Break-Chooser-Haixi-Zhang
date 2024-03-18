package sakura.springbreakchooser

object LanguageTable {

    var languageNameMapping: Map<String, String> = mapOf(
        "en-US" to "English",
        "es-US" to "Spanish",
        "ja-JP" to "Japanese",
        "fr-FR" to "French")

    var supportSource: Array<String> = arrayOf("en-US")
    var supportDestination: Array<String> = arrayOf("es-US", "ja-JP", "fr-FR")

}