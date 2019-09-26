package com.example.immortarjourney

import android.content.Context
import android.media.Image
import android.os.AsyncTask
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.io.DataOutputStream
import android.media.AudioManager




class WebUploadTask(private val mixing: Mixing) : AsyncTask<ByteArray, Int, String?>() {
    private var statusText: String? = null

    override fun doInBackground(vararg images: ByteArray): String? {
        statusText = null
        for (image in images) {
            val res = uploadImage(image)
            if (res != null)
                return res
        }
        return null
    }

    private fun uploadImage(image: ByteArray): String? {
        try {
            val url = URL("http://130.211.97.48:8080/image.jpg")

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                useCaches = false
                doOutput = true
                // Write image

                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Size", image.size.toString())

                val out = outputStream
                out.write(image)

                out.flush()
                out.close()

                val code = responseCode
                if (code < 200 || code >= 300) {
                    return "Image upload failed with return code $code"
                }

                inputStream.bufferedReader().use {
                    statusText = it.readText()
                }

                disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Failed to upload image"
        }
        return null
    }

    private fun uploadImageMultipart(image: Image): String? {
        try {
            val url = URL("http://130.211.97.48:8080")

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                useCaches = false
                doOutput = true

                val boundary = "===" + System.currentTimeMillis() + "==="
                val filename = "image.jpg"

                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data;boundary=$boundary")

                val out = DataOutputStream(outputStream)

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$filename\";filename=\"$filename\"\r\n\r\n")

                // Write image
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                out.write(bytes)

                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
                out.close()

                val code = responseCode
                if (code != 200) {
                    return "Image upload failed with return code $code"
                }

                /*inputStream.bufferedReader().use {
                    it.lines().forEach { line ->
                        println(line)
                    }
                }*/

                disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Failed to upload image"
        } finally {
            image.close()
        }
        return null
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
        mixing.uploading -= 1
        if (statusText != null) {
            val text = statusText!!.trim()
            val percentage = text.toFloatOrNull()
            if (percentage != null) {
                val fraction = 1F / 14F
                mixing.sensorData.moistness = (mixing.sensorData.moistness ?: percentage) * (1 - fraction) + percentage * fraction
                mixing.updateSensorData()
                /*val percent = (percentage * 100).toString() + " %"
                //mixing.findViewById<TextView>(R.id.instruction_text).text = percent
                if (mixing.ttsReady && !mixing.tts.isSpeaking) {
                    if (mixing.tts.speak(
                            percent,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            text
                        ) != TextToSpeech.SUCCESS
                    ) {
                        Log.println(Log.INFO, "Mortar-TTS", "Failed to start tts")
                        Toast.makeText(
                            mixing.applicationContext,
                            "Text to Speech failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    val audio = mixing.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val musicVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

                    if (musicVolume <= 2) {
                        Toast.makeText(mixing, "Turn up your music volume!", Toast.LENGTH_SHORT)
                            .show()
                    }
                }*/
            }
        }

        if (result == null)
            return
        Toast.makeText(mixing.applicationContext, result, Toast.LENGTH_SHORT).show()
    }
}