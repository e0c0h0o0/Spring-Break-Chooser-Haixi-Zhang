package sakura.springbreakchooser

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sakura.springbreakchooser.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.random.Random


class SpringBreakSensorEventListener(val mainActivity: MainActivity): SensorEventListener {

    var lastUpdate = System.currentTimeMillis()
    var lastX = 0.0f
    var lastY = 0.0f
    var lastZ = 0.0f
    val SHAKE_THRESHOLD = 300f

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.getType() === Sensor.TYPE_LINEAR_ACCELERATION) {
                val curTime = System.currentTimeMillis()
                if (curTime - lastUpdate > 100) {
                    val diffTime: Long = curTime - lastUpdate
                    lastUpdate = curTime
                    val x: Float = event.values.get(0)
                    val y: Float = event.values.get(1)
                    val z: Float = event.values.get(2)
                    val speed: Float = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000
                    // Log.wtf("&&&", "speed: " + speed)
                    if (speed > SHAKE_THRESHOLD) {
                        // Toast.makeText(mainActivity, "Shake detected", Toast.LENGTH_SHORT).show()
                        mainActivity.onShakeDetected()
                    }
                    lastX = x
                    lastY = y
                    lastZ = z
                }
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) { }

}

class SpringBreakRecognitionListener(val mainActivity: MainActivity): RecognitionListener {
    override fun onReadyForSpeech(p0: Bundle?) {
        Log.wtf("SpringBreakRecognitionListener", "::onReadyForSpeech")
        mainActivity.runOnUiThread {
            mainActivity.binding.uStartRecording.text = "Listening..."
            mainActivity.appendTextview("Please say something:")
        }
    }

    override fun onBeginningOfSpeech() {
        Log.wtf("SpringBreakRecognitionListener", "::onBeginningOfSpeech {{{ ")
    }

    override fun onEndOfSpeech() {
        Log.wtf("SpringBreakRecognitionListener", " }}} ::onEndOfSpeech")
    }

    override fun onError(p0: Int) {
        Log.wtf("SpringBreakRecognitionListener", "::onError -> $p0")

        var mError = ""
        mError = when(p0) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NETWORK -> "Network"
            SpeechRecognizer.ERROR_AUDIO -> "Audio"
            SpeechRecognizer.ERROR_SERVER -> "Server"
            SpeechRecognizer.ERROR_CLIENT -> "Client"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech time out"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            else -> "ERRNO: $p0"
        }

        mainActivity.runOnUiThread {
            mainActivity.binding.uStartRecording.text = "Start Recording"
            mainActivity.appendTextview("Error detected: " + mError)
        }
    }

    override fun onResults(p0: Bundle?) {
        val result = p0?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        mainActivity.runOnUiThread {
            mainActivity.binding.uStartRecording.text = "Start Recording"
            mainActivity.appendTextview("Source:")
            var content = ""
            result?.forEach {
                mainActivity.appendTextview(it)
                content += it
            }

            mainActivity.translator.translate(
                content,
                {isSuccess, translatedContent ->
                    if(isSuccess) {
                        mainActivity.runOnUiThread {
                            mainActivity.appendTextview("Translated: ")
                            mainActivity.appendTextview(translatedContent)
                        }
                        mainActivity.playVoice(translatedContent)
                    }
                    else {
                        mainActivity.appendTextview("Translate service error")
                    }
                }
            )
        }
    }

    override fun onRmsChanged(p0: Float) { }
    override fun onBufferReceived(p0: ByteArray?) { Log.wtf("SpringBreakRecognitionListener", "::onBufferReceived") }
    override fun onPartialResults(p0: Bundle?) { Log.wtf("SpringBreakRecognitionListener", "::onPartialResults") }
    override fun onEvent(p0: Int, p1: Bundle?) { Log.wtf("SpringBreakRecognitionListener", "::onEvent") }
}

class SpringBreakOnInitListener(val mainActivity: MainActivity): OnInitListener {
    override fun onInit(p0: Int) {
        mainActivity.flagTTSEngineReady = true
    }
}


class MainActivity : AppCompatActivity() {

    val PERMISSION_REQUESST_CODE = 0
    var flagPermissionRecordAudioGranted = false
    lateinit var speechRecognizer: SpeechRecognizer
    lateinit var binding: ActivityMainBinding
    lateinit var translator: TranslateService
    lateinit var textToSpeechEngine: TextToSpeech
    var flagTTSEngineReady = false
    lateinit var sensorManager: SensorManager

    var sourceIndex = 0
    var destinationIndex = 0
    lateinit var placeClient: PlacesClient
    lateinit var springBreakSensorEventListener: SpringBreakSensorEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        springBreakSensorEventListener = SpringBreakSensorEventListener(this)


        tryInitializeSpeechRecognizer()
        initializeView()
        setupTranslator()
        initializeTTS()
        initializePlaceClient()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            springBreakSensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
            SensorManager.SENSOR_DELAY_NORMAL
        )


        // textSearch()
    }

    fun onShakeDetected() {
        textSearch()
    }

    fun initializePlaceClient() {
        val app: ApplicationInfo = packageManager
            .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val bundle = app.metaData
        val apiKey = bundle.getString("com.google.android.geo.API_KEY")
        apiKey?.let { Places.initializeWithNewPlacesApiEnabled(applicationContext, it) }
        placeClient = Places.createClient(this)
    }

    fun textSearch() {
        val country = Locale("", LanguageTable.supportDestination[destinationIndex].split("-")[1]).getDisplayCountry()
        val placeFields = arrayOf(Place.Field.LAT_LNG).toList()
        val searchByTextRequest = SearchByTextRequest.builder(
            country + " " + "Attractions",
            placeFields
        )
            .setMaxResultCount(5)
            .build()

        placeClient.searchByText(searchByTextRequest)
            .addOnSuccessListener {
                val target = it.places[Math.abs(Random.nextInt()) % 5]
                var uri = "geo:" + target.latLng.latitude + "," + target.latLng.longitude
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                startActivity(intent)
            }
            .addOnFailureListener {
                Log.wtf("###", "Search fail: "+ it.toString())
            }
    }

    fun playVoice(text: String) {
        textToSpeechEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null)
    }

    fun initializeTTS() {
        val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                textToSpeechEngine = TextToSpeech(this, SpringBreakOnInitListener(this))
                Log.wtf(javaClass.simpleName, "TTS Engine initialize success")
                textToSpeechEngine.setLanguage(Locale(LanguageTable.supportDestination[destinationIndex].split("-")[0]))
            }
            else {
               val installIntent = Intent()
               installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
               startActivity(installIntent)
                Log.wtf(javaClass.simpleName, "TTS Engine initialize fail")
            }
        }

        val checkIntent = Intent()
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        resultLauncher.launch(checkIntent)
    }

    fun setupTranslator() {
        translator = TranslateService(LanguageTable.supportSource[sourceIndex], LanguageTable.supportDestination[destinationIndex])

        GlobalScope.launch {
            var count = 0
            while(!translator.flagTranslatorReady) {
                count++
                if(count > 30) {
                    break
                }
                Thread.sleep(1000)
            }

            if(!translator.flagTranslatorReady) {
                runOnUiThread {
                    appendTextview("Translator initialize fail")
                }
            }
            else {
                Log.wtf(javaClass.simpleName, "Translator initialize success")
            }
        }
    }

    fun appendTextview(msg: String) {
        binding.uPrompt.text = binding.uPrompt.text.toString() + msg + "\n"
    }

    fun onSourceLanguageChanged() {
        setupTranslator()
    }

    fun onDestinationLanguageChanged() {
        textToSpeechEngine.setLanguage(Locale(LanguageTable.supportDestination[destinationIndex].split("-")[0]))
        setupTranslator()
    }

    fun initializeView() {

        binding.uLanguageSource.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(sourceIndex != position) {
                    sourceIndex = position
                    onSourceLanguageChanged()
                }
            }
        }
        binding.uLanguageDestion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(destinationIndex != position) {
                    destinationIndex = position
                    onDestinationLanguageChanged()
                }
            }
        }

        binding.uStartRecording.setOnClickListener {
            Log.wtf(javaClass.simpleName, "uStartRecording.setOnClickListener clicked")
            if(!translator.flagTranslatorReady) {
                Toast.makeText(this, "Translator is not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            speechRecognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.forLanguageTag(LanguageTable.supportSource[sourceIndex])
            )
            speechRecognizer.setRecognitionListener(SpringBreakRecognitionListener(this))
            speechRecognizer.startListening(speechRecognizerIntent)

        }
    }

    fun tryInitializeSpeechRecognizer() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkPermission()
        }
        else {
            initializeSpeechRecognizer()
        }
    }

    fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

    }

    fun checkPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUESST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            PERMISSION_REQUESST_CODE -> {
                permissions.forEachIndexed {
                    index, s ->
                    if(s == Manifest.permission.RECORD_AUDIO) {
                        if(grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                            flagPermissionRecordAudioGranted = true
                        }
                    }
                }
            }
        }
    }

}