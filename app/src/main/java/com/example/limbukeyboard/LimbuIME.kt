package com.example.limbukeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.IOException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class LimbuIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var limbuKeyboard: Keyboard? = null
    private var englishKeyboard: Keyboard? = null // You can define this separately if needed
    private var keyboardView: KeyboardView? = null
    private var currentKeyboard: Keyboard? = null

    // For word suggestions
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(List::class.java)
    private var dictionary: List<Map<String, Any>>? = null // Simplified type, consider creating a data class

    override fun onCreate() {
        super.onCreate()
        // Initialize keyboards
        limbuKeyboard = Keyboard(this, R.xml.limbu_keyboard)
        // englishKeyboard = Keyboard(this, R.xml.english_keyboard) // Define if needed
        currentKeyboard = limbuKeyboard

        // Load dictionary asynchronously
        loadDictionary()
    }

    private fun loadDictionary() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/ingsha09/limbu-dictionary-api/main/data.json?v=3")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val responseBody = response.body?.string()
                    // Note: Parsing with Moshi needs a specific data class. This is simplified.
                    // A better approach is to define a data class like LimbuWord.
                    // For now, using a generic map structure.
                    // Consider defining: data class LimbuWord(val id: String, val limbu: String, val phonetic: String, val meaning: Map<String, String>, val group: String?, val status: String)
                    // And change adapter type accordingly.
                    dictionary = adapter.fromJson(responseBody) as List<Map<String, Any>>?
                    // Sorting logic can be applied here if needed after parsing.
                    // val sortedDict = sortLimbu(dictionary ?: emptyList()) // Implement sortLimbu function
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log the error
            }
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.input_method, null) as KeyboardView
        keyboardView?.setKeyboard(currentKeyboard)
        keyboardView?.setOnKeyboardActionListener(this)
        return keyboardView!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Set the initial keyboard
        keyboardView?.setKeyboard(currentKeyboard)
        keyboardView?.isEnabled = true
    }

    override fun onPress(primaryCode: Int) {
        // Handle key press visual feedback if needed
    }

    override fun onRelease(primaryCode: Int) {
        // Handle key release visual feedback if needed
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                inputConnection?.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_SHIFT -> {
                // Toggle shift state if needed
            }
            Keyboard.KEYCODE_CANCEL -> {
                // Dismiss keyboard if needed
                requestHideSelf(0)
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                // Switch between Limbu and English keyboard
                // currentKeyboard = if (currentKeyboard == limbuKeyboard) englishKeyboard else limbuKeyboard
                // keyboardView?.setKeyboard(currentKeyboard)
            }
            else -> {
                // Handle character input
                val codePoint = primaryCode.toChar()
                inputConnection?.commitText(codePoint.toString(), 1)

                // --- Word Suggestion Logic ---
                // Fetch the current text in the input field (or a prefix)
                val currentText = inputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: "" // Get last 50 chars
                // Find suggestions based on currentText from the loaded dictionary
                // This is a simplified example, you'd need more sophisticated matching
                val suggestions = findSuggestions(currentText)

                // Display suggestions (this is a simplified placeholder)
                // Android InputMethodService has methods like setInputExtras or setting a candidate view,
                // but implementing a full candidate view is complex.
                // You might just set the text or use a popup window.
                // For now, just log the suggestions or use setComposingText for basic preview.
                if (suggestions.isNotEmpty()) {
                    // Example: Show first suggestion as composing text
                    inputConnection?.setComposingText(suggestions[0]["limbu"] as String, 1)
                }
            }
        }
    }

    private fun findSuggestions(prefix: String): List<Map<String, Any>> {
        // This function should search the dictionary for words starting with 'prefix'
        // and return a list of potential matches.
        // Use the sorting logic defined in the dictionary API documentation.
        val limbuAlphabet = listOf('ᤀ', 'ᤁ', 'ᤂ', 'ᤃ', 'ᤄ', 'ᤅ', 'ᤆ', 'ᤇ', 'ᤈ', 'ᤋ', 'ᤌ', 'ᤍ', 'ᤎ', 'ᤏ', 'ᤐ', 'ᤑ', 'ᤒ', 'Tool  does not exists.It seems there was an issue with the previous response generation. Let's continue constructing the `LimbuIME.kt` file, focusing on the word suggestion logic using the fetched `data.json`.
