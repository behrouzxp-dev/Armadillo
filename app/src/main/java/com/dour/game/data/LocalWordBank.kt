package com.dour.game.data

import android.content.Context
import org.json.JSONObject
import com.dour.game.game.WordCategories
import com.dour.game.game.WordSource
import com.dour.game.game.normalizeWord
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Random

/** Offline, bundled Persian word bank. A game's selected categories never change mid-game. */
class LocalWordBank(context: Context, private val random: Random = Random()) : WordSource {
    private val topicLevels: Map<String, Map<Int, List<String>>> = loadTopics(context)

    override fun nextWord(
        categories: Set<String>,
        difficulty: Int,
        used: MutableSet<String>,
        legacyGeneralDifficulty: Int?
    ): String {
        val words = combinedWords(categories, difficulty, legacyGeneralDifficulty)
        require(words.isNotEmpty()) { "No local words for selected categories and difficulty $difficulty" }
        var candidates = words.filter { normalizeWord(it) !in used }
        if (candidates.isEmpty()) {
            // All words in this game's selected pool were shown. A fresh cycle is now allowed.
            used.clear()
            candidates = words
        }
        return candidates[random.nextInt(candidates.size)]
    }

    override fun count(categories: Set<String>, difficulty: Int, legacyGeneralDifficulty: Int?): Int =
        combinedWords(categories, difficulty, legacyGeneralDifficulty).size

    private fun combinedWords(categories: Set<String>, difficulty: Int, legacyGeneralDifficulty: Int?): List<String> {
        require(categories.isNotEmpty() && categories.all { it in WordCategories.all }) {
            "At least one known word category must be selected"
        }
        require(difficulty in 1..3) { "Difficulty tier must be between 1 and 3" }
        val unique = linkedMapOf<String, String>()
        for (category in categories) {
            // The legacy general bank is intentionally not consulted by the Armadillo game.
            val selected = topicLevels[category]?.get(difficulty).orEmpty()
            selected.forEach { word ->
                val key = normalizeWord(word)
                if (!unique.containsKey(key)) unique[key] = word
            }
        }
        return unique.values.toList()
    }

    private fun loadTopics(context: Context): Map<String, Map<Int, List<String>>> {
        val json = readJson(context, "categories.json")
        val result = linkedMapOf<String, Map<Int, List<String>>>()
        for (category in listOf(
            WordCategories.JOBS,
            WordCategories.ANIMALS,
            WordCategories.FOODS,
            WordCategories.PLACES,
            WordCategories.TECHNOLOGY
        )) {
            val categoryJson = json.optJSONObject(category)
                ?: throw IllegalStateException("Missing topic category $category")
            val levels = (1..3).associateWith { level ->
                val array = categoryJson.optJSONArray(level.toString())
                    ?: throw IllegalStateException("Missing $category difficulty $level")
                check(array.length() == 100) { "$category level $level must contain exactly 100 words" }
                val words = buildList {
                    for (index in 0 until array.length()) {
                        val value = array.optString(index).trim()
                        check(value.isNotEmpty()) { "Blank entry in $category level $level" }
                        add(value)
                    }
                }
                check(words.map(::normalizeWord).toSet().size == 100) {
                    "Duplicate in $category level $level"
                }
                words
            }
            result[category] = levels
        }
        val newWords = result.values.flatMap { it.values.flatten() }
        check(newWords.size == 1500) { "The five playable categories must contain 1,500 entries" }
        check(newWords.map(::normalizeWord).toSet().size == 1500) {
            "Playable categories must not duplicate words across categories or difficulty levels"
        }
        return result
    }

    private fun readJson(context: Context, assetName: String): JSONObject {
        val text = context.assets.open(assetName).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }
        return JSONObject(text)
    }
}
