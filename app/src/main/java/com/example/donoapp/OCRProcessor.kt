package com.example.donoapp

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import java.text.ParseException

class OCRProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Regular expression to capture various date formats
    private val dateRegex = """\b(3[01]|[12][0-9]|0?[1-9])[ /.-]?(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[ /.-]?(\d{2}|\d{4})\b""".toRegex(RegexOption.IGNORE_CASE)

    fun validateDate(year: Int): Boolean {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        // Allow for dates within the past 50 years and 10 years into the future
        return year in (currentYear - 50)..(currentYear + 10)
    }
    fun formatDate(dateStr: String): String? {
        val patterns = listOf(
            "ddMMMyy", "dd MMM yy", "dd-MM-yy",
            "ddMMMyyyy", "dd MMM yyyy", "dd-MM-yyyy"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.isLenient = false // set lenient to false to avoid parsing incorrect dates
                val date = sdf.parse(dateStr)
                val calendar = Calendar.getInstance()
                calendar.time = date
                if (validateDate(calendar.get(Calendar.YEAR))) {
                    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                    return formatter.format(date)
                }
            } catch (e: ParseException) {
                // Handle parsing exceptions if the date format does not match
            }
        }
        return null // Return null if no valid date format is found or date is not valid
    }
    fun processImage(image: InputImage, callback: (String) -> Unit, dateCallback: (String?) -> Unit) {
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                callback(visionText.text)  // Call with all detected text

                // Extract dates from the text and format them
                val dateMatches = dateRegex.findAll(visionText.text).map {
                    it.value
                }.toList()

                val formattedDates = dateMatches.mapNotNull { formatDate(it) }.joinToString("\n")

                if (formattedDates.isNotEmpty()) {
                    dateCallback(formattedDates)  // Call with formatted dates
                } else {
                    dateCallback(null)  // No dates found
                }
            }
            .addOnFailureListener { e ->
                println("OCR failed with error: $e")
            }
    }
}