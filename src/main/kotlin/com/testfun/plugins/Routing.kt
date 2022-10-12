@file:OptIn(KtorExperimentalLocationsAPI::class)

package com.testfun.plugins

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*
import java.util.stream.Collectors

fun Application.configureRouting() {
    val client = HttpClient()
    val dict = File("/usr/share/dict/words")
        .readLines()
        .filterNot { it.contains("-") }

    install(Locations)

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        get<MyLocation> {
            call.respondText("Location: name=${it.name}, arg1=${it.arg1}, arg2=${it.arg2}")
        }

        // Register nested routes
        get<Type.Edit> {
            call.respondText("Inside $it")
        }
        get<Type.List> {
            call.respondText("Inside $it")
        }

        get<CustomLocation> {
            println(it)
            call.respond(it)
        }

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

@Location("/location/{name}")
class MyLocation(val name: String, val arg1: Int = 42, val arg2: String = "default")

@Serializable
@Location("/custom/{name}")
data class CustomLocation(val name: String, val arg1: Int = 42, val arg2: String = "default")

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

@Location("/type/{name}")
data class Type(val name: String) {
    @Location("/edit")
    data class Edit(val type: Type)

    @Location("/list/{page}")
    data class List(val type: Type, val page: Int)
}
