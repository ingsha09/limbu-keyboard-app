// app/src/main/java/com/example/limbukeyboard/LimbuIME.kt
package com.example.limbukeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types // <-- Add this import
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.IOException
import java.util.concurrent.ConcurrentHashMap

// Data class to represent a Limbu word entry from the JSON
data class LimbuWord(
    val id: String,
    val limbu: String,
    val phonetic: String,
    val meaning: Meaning,
    val group: String?,
    val status: String
) {
    data class Meaning(
        val en: String,
        val ne: String
    )
}

class LimbuIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var limbuKeyboard: Keyboard? = null
    private var englishKeyboard: Keyboard? = null // You can define this separately if needed
    private var keyboardView: KeyboardView? = null
    private var currentKeyboard: Keyboard? = null

    // For word suggestions
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    // Use Types.newParameterizedType to correctly parse the List<LimbuWord> [[6]]
    private val adapter = moshi.adapter<List<LimbuWord>>(Types.newParameterizedType(List::class.java, LimbuWord::class.java))
    private var dictionary: List<LimbuWord>? = null
    private val dictionaryMap = ConcurrentHashMap<String, LimbuWord>() // Use a map for faster prefix lookups

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
                    dictionary = adapter.fromJson(responseBody)

                    // Populate the map for faster lookup
                    dictionary?.forEach { word ->
                        dictionaryMap[word.limbu] = word
                    }
                    // Optionally sort the list here if needed for display order
                    // dictionary = sortLimbu(dictionary ?: emptyList())
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log the error
            }
        }
    }

    // Function to sort the Limbu words based on the predefined alphabet
    private fun sortLimbu(data: List<LimbuWord>): List<LimbuWord> {
        val limbuAlphabet = listOf('ᤀ', 'ᤁ', 'ᤂ', 'ᤃ', 'ᤄ', 'ᤅ', 'ᤆ', 'ᤇ', 'ᤈ', 'ᤋ', 'ᤌ', 'ᤍ', 'ᤎ', 'ᤏ', 'ᤐ', 'ᤑ', 'ᤒ', 'ᤓ', 'ᤔ', 'ᤕ', '</tool_call>', 'ᤗ', 'ᤘ', 'ᤙ', 'ᤛ', 'ᤜ')
        return data.sortedWith { a, b ->
            val firstCharA = a.limbu.firstOrNull() ?: return@sortedWith 0
            val firstCharB = b.limbu.firstOrNull() ?: return@sortedWith 0

            val idxA = limbuAlphabet.indexOf(firstCharA)
            val idxB = limbuAlphabet.indexOf(firstCharB)

            if (idxA != idxB) {
                idxA.compareTo(idxB)
            } else {
                a.limbu.compareTo(b.limbu)
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
                val currentText = inputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: ""
                // Find suggestions based on currentText from the loaded dictionary
                val suggestions = findSuggestions(currentText)

                // Display suggestions (this is a simplified placeholder)
                // A full candidate view implementation is complex.
                // Using setComposingText for a basic preview of the first suggestion.
                if (suggestions.isNotEmpty()) {
                    // Example: Show first suggestion as composing text
                    // Note: This might overwrite the user's typing. A proper candidate view is better.
                    inputConnection?.setComposingText(suggestions[0].limbu, 1)
                }
            }
        }
    }

    private fun findSuggestions(prefix: String): List<LimbuWord> {
        // This function searches the dictionary for words starting with 'prefix'
        // and returns a list of potential matches.
        // It uses the map for potentially faster initial filtering if the prefix is a whole word,
        // but for prefix matching, iterating the sorted list might be more direct.
        val results = mutableListOf<LimbuWord>()
        if (prefix.isEmpty()) return results

        val sortedDict = dictionary ?: emptyList() // Use the sorted list if it was sorted during loading
        for (word in sortedDict) {
            if (word.limbu.startsWith(prefix, ignoreCase = false)) { // Adjust case sensitivity if needed
                results.add(word)
                if (results.size >= 5) break // Limit suggestions
            } else if (results.isNotEmpty() && word.limbu > prefix) {
                // If the current word is lexicographically greater than the prefix
                // and we already have matches, we can potentially break early if the list is sorted correctly.
                // This relies on the sorting logic being applied to the 'dictionary' list.
                break
            }
        }
        return results
    }


    override fun onText(text: CharSequence?) {
        // Handle text input if needed (e.g., for multi-character keys)
        text?.let {
            currentInputConnection?.commitText(it, 1)
        }
    }

    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
