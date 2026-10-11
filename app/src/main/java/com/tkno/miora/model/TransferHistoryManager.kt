package com.tkno.miora.model

import android.content.Context
import android.content.SharedPreferences
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
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                // Filter out any legacy dummy records
                if (id in listOf("init_1", "init_2", "init_3")) continue

                list.add(
                    TransferRecord(
                        id = id,
                        fileName = obj.getString("fileName"),
                        fileSize = obj.getLong("fileSize"),
                        direction = TransferDirection.valueOf(obj.getString("direction")),
                        protocol = TransferProtocol.valueOf(obj.getString("protocol")),
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

    fun addRecord(record: TransferRecord) {
        val updated = listOf(record) + _history.value
        _history.value = updated
        saveHistory(updated)
    }

    fun removeRecord(id: String) {
        val updated = _history.value.filter { it.id != id }
        _history.value = updated
        saveHistory(updated)
    }

    fun clearAll() {
        _history.value = emptyList()
        saveHistory(emptyList())
    }
}
