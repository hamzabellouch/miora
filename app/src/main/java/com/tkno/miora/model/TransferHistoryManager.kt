package com.tkno.miora.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object TransferHistoryManager {
    private const val PREF_KEY_HISTORY = "miora_transfer_history_json"
    private lateinit var prefs: SharedPreferences
    private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history: StateFlow<List<TransferRecord>> = _history.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var saveJob: Job? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
        loadHistory()
    }

    private fun loadHistory() {
        val raw = prefs.getString(PREF_KEY_HISTORY, null)
        if (raw.isNullOrEmpty()) {
            _history.value = emptyList()
            return
        }

        try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<TransferRecord>()
            val compositeRegex = Regex(""" \+\d+$""")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                // Filter out any legacy dummy records or composite summary records (e.g. "image.jpg +2")
                if (id in listOf("init_1", "init_2", "init_3")) continue
                val fileName = obj.getString("fileName")
                if (compositeRegex.containsMatchIn(fileName)) continue

                list.add(
                    TransferRecord(
                        id = id,
                        fileName = fileName,
                        fileSize = obj.getLong("fileSize"),
                        direction = TransferDirection.valueOf(obj.getString("direction")),
                        protocol = try { TransferProtocol.valueOf(obj.getString("protocol")) } catch (_: Exception) { TransferProtocol.WIFI_DIRECT },
                        timestamp = obj.getLong("timestamp"),
                        status = TransferStatus.valueOf(obj.getString("status")),
                        progress = obj.getDouble("progress").toFloat(),
                        speedMbps = obj.optDouble("speedMbps", 0.0).toFloat(),
                        uriString = if (obj.has("uriString") && !obj.isNull("uriString")) obj.getString("uriString") else null
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            _history.value = emptyList()
        }
    }

    private fun saveHistory(list: List<TransferRecord>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("fileSize", item.fileSize)
                    put("direction", item.direction.name)
                    put("protocol", item.protocol.name)
                    put("timestamp", item.timestamp)
                    put("status", item.status.name)
                    put("progress", item.progress.toDouble())
                    put("speedMbps", item.speedMbps.toDouble())
                    put("uriString", item.uriString)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(PREF_KEY_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleSaveHistory() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(350)
            saveHistory(_history.value)
        }
    }

    fun addRecord(record: TransferRecord) {
        val updated = (listOf(record) + _history.value.filter { it.id != record.id }).take(500)
        _history.value = updated
        scheduleSaveHistory()
    }

    fun removeRecord(id: String) {
        val updated = _history.value.filter { it.id != id }
        _history.value = updated
        saveHistory(updated)
    }

    fun clearAll() {
        saveJob?.cancel()
        _history.value = emptyList()
        saveHistory(emptyList())
    }
}
