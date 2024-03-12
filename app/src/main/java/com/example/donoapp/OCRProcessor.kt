package com.example.donoapp

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OCRProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun processImage(image: InputImage, callback: (String) -> Unit) {
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                callback(visionText.text) // This is where the recognized text is returned
            }
            .addOnFailureListener { e ->
                // Handle failure in text recognition
            }
    }
}
