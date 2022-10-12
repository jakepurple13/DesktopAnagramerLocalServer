@file:OptIn(KtorExperimentalLocationsAPI::class)

package com.testfun.plugins

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

fun Application.configureAnagramer() {
    val dict = File("/usr/share/dict/words")
        .readLines()
        .filterNot { it.contains("-") }

    routing {
        get<RandomWord> { word ->
            println(word)
            val w = dict
                .filter { it.length == word.size }
                .random()
            println(w)
            val words = dict
                .filter { it.length >= word.minimumSize && compare(w, it) }
                .distinctBy { it.lowercase() }
            call.respond(Word(w, words))
        }

        get<WordDefinition> {
            println(it)
            call.respond(Definition(it.word, RunPython.runPythonCodeAsync("get_definition.py", it.word).await()))
        }
    }
}

private fun compare(word: String, anagram: String): Boolean {
    val c = word.groupBy { it.lowercaseChar() }.mapValues { it.value.size }
    val a = anagram.groupBy { it.lowercaseChar() }.mapValues { it.value.size }

    for (i in a) {
        c[i.key]?.let { if (it < i.value) return false } ?: return false
    }

    return true
}

@Serializable
@Location("/randomWord/{size}")
data class RandomWord(val size: Int, val minimumSize: Int = 3)

@Serializable
@Location("wordDefinition/{word}")
data class WordDefinition(val word: String)

@Serializable
data class Definition(val word: String, val definition: String)

@Serializable
data class Word(val word: String, val anagrams: List<String>)

object RunPython {
    @Suppress("BlockingMethodInNonBlockingContext")
    fun runPythonCodeAsync(fileName: String, vararg args: String) = GlobalScope.async {
        val command = "python3 src/main/python/$fileName ${args.joinToString(" ")}"
        val process = Runtime.getRuntime().exec(command)
        process.waitFor()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        reader.lines().collect(Collectors.joining("\n"))
    }
}