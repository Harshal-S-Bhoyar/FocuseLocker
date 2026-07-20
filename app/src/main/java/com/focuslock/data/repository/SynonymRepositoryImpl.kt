package com.focuslock.data.repository

import android.content.Context
import com.focuslock.domain.repository.SynonymRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SynonymRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SynonymRepository {

    override fun getSynonyms(): Map<String, List<String>> {
        return try {
            val jsonStr = context.assets.open("keyword_synonyms.json")
                .bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, List<String>>()
            for (key in json.keys()) {
                val array = json.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                map[key] = list
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
