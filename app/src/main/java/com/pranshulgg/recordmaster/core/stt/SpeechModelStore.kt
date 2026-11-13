package com.pranshulgg.recordmaster.core.stt

import com.pranshulgg.recordmaster.core.data.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * The set of models the app knows about and which one is selected.
 *
 * Built-in catalog entries are always present; user imports are persisted as
 * JSON in DataStore. A missing/blank selection means the built-in default.
 */
class SpeechModelStore(private val prefs: PreferencesManager) {

    val models: Flow<List<SpeechModel>> =
        prefs.sttImportedModelsFlow.map { json ->
            SpeechModelCatalog.BUILT_INS + decode(json)
        }

    val selectedModelId: Flow<String> =
        prefs.sttModelIdFlow.map { it.ifBlank { SpeechModelCatalog.DEFAULT.id } }

    val selectedModel: Flow<SpeechModel?> =
        combine(models, selectedModelId) { list, id ->
            list.firstOrNull { it.id == id }
                ?: list.firstOrNull { it.id == SpeechModelCatalog.DEFAULT.id }
        }

    suspend fun select(id: String) = prefs.setSttModelId(id)

    suspend fun add(model: SpeechModel) {
        val imported = decode(prefs.sttImportedModelsJson())
            .filterNot { it.id == model.id }
        prefs.setSttImportedModelsJson(encode(imported + model))
    }

    /** Renames an imported model. Ids and file names never change. */
    suspend fun rename(id: String, name: String) {
        val imported = decode(prefs.sttImportedModelsJson()).map { model ->
            if (model.id == id) {
                model.copy(displayName = normalizeName(name, model.displayName))
            } else {
                model
            }
        }
        prefs.setSttImportedModelsJson(encode(imported))
    }

    suspend fun remove(id: String) {
        val imported = decode(prefs.sttImportedModelsJson()).filterNot { it.id == id }
        prefs.setSttImportedModelsJson(encode(imported))
        if (prefs.sttModelId() == id) {
            prefs.setSttModelId(SpeechModelCatalog.DEFAULT.id)
        }
    }

    companion object {
        private const val MAX_NAME_LENGTH = 60

        fun decode(json: String): List<SpeechModel> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { index ->
                    val obj = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = obj.optString("id")
                    val fileName = obj.optString("fileName")
                    if (id.isBlank() || fileName.isBlank()) return@mapNotNull null
                    SpeechModel(
                        id = id,
                        displayName = obj.optString("displayName", fileName),
                        fileName = fileName,
                        source = SpeechModelSource.IMPORTED,
                        sizeBytes = obj.optLong("sizeBytes"),
                        modelType = obj.optString("modelType", "unknown"),
                        multilingual = obj.optBoolean("multilingual", true),
                        sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() },
                        importedAt = obj.optLong("importedAt").takeIf { it > 0 }
                    )
                }
            }.getOrDefault(emptyList())
        }

        fun encode(models: List<SpeechModel>): String {
            val array = JSONArray()
            models.filterNot { it.isBuiltIn }.forEach { model ->
                array.put(JSONObject().apply {
                    put("id", model.id)
                    put("displayName", model.displayName)
                    put("fileName", model.fileName)
                    put("sizeBytes", model.sizeBytes)
                    put("modelType", model.modelType)
                    put("multilingual", model.multilingual)
                    model.sourceUrl?.let { put("sourceUrl", it) }
                    model.importedAt?.let { put("importedAt", it) }
                })
            }
            return array.toString()
        }

        fun normalizeName(raw: String, fallback: String): String {
            val trimmed = raw.trim()
                .replace(Regex("\\s+"), " ")
                .take(MAX_NAME_LENGTH)
            return trimmed.ifBlank { fallback }
        }
    }
}
