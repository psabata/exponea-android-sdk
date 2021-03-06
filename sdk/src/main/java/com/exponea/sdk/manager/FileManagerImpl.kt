package com.exponea.sdk.manager

import android.content.Context
import com.exponea.sdk.models.ExponeaConfiguration
import com.exponea.sdk.util.Logger
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.File

class FileManagerImpl : FileManager {

    val gson = Gson()

    override fun createFile(filename: String, type: String) {
        createTempFile(
                prefix = filename,
                suffix = type
        )
    }

    override fun writeToFile(filename: String, text: String) {
        val filePath = ClassLoader.getSystemResource(filename).file
        val bufferedWriter = File(filePath).bufferedWriter()
        bufferedWriter.write(text)
    }

    override fun readContentFromDefaultFile(context: Context): String? {
        return try {

            val configurationFileName = "exponea_configuration.json"
            var inputStream = javaClass.classLoader.getResourceAsStream(configurationFileName)

            if (inputStream == null)
                inputStream = context.assets.open(configurationFileName)

            val buffer = inputStream.bufferedReader()

            val inputString = buffer.use { it.readText() }
            Logger.d(this, "Configuration file successfully loaded")
            inputString
        } catch (e: Exception) {
            Logger.e(this, "Could not load configuration file ")
            null
        }
    }

    override fun readContentFromFile(filename: String): String? {
        return try {
            val filePath = ClassLoader.getSystemResource(filename).file
            val bufferedReader: BufferedReader = File(filePath).bufferedReader()
            val inputString = bufferedReader.use { it.readText() }
            Logger.d(this, "Configuration file successfully loaded")
            inputString
        } catch (e: Exception) {
            Logger.e(this, "Could not load configuration file $filename")
            null
        }
    }

    override fun deleteFile(filename: String) {
        val filePath = ClassLoader.getSystemResource(filename).file
        val file = File(filePath)
        try {
            file.delete()
            Logger.d(this, "File $filename was deleted.")
        } catch (e: Exception) {
            Logger.e(this, "Could not delete file: $filename")
        }

    }

    override fun getConfigurationFromFile(filename: String): ExponeaConfiguration? {
        val data = readContentFromFile(filename)

        if (data.isNullOrEmpty()) {
            Logger.e(this, "No data found on Configuration file $filename")
            return null
        }

        return gson.fromJson(data, ExponeaConfiguration::class.java)
    }


    override fun getConfigurationFromDefaultFile(buffer: Context): ExponeaConfiguration? {
        val data = readContentFromDefaultFile(buffer)

        if (data.isNullOrEmpty()) {
            Logger.e(this, "No data found on Configuration file")
            return null
        }

        return gson.fromJson(data, ExponeaConfiguration::class.java)
    }
}