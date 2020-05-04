/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package githubnames

import java.io.File
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

class App {
    val greeting: String
        get() {
            return "Hello world."
        }
}

fun main(args: Array<String>) {
    val token = getToken(args)
    sendGet("https://api.github.com/users/j0ner0n/repos", token)
}

fun getToken(args: Array<String>): String {
    val token: String
    val f = File(".github-oauth.token")
    token = if (f.exists() && f.canRead() && f.readText() != "") {
        f.readText()
    } else if (args.size == 1) {
        args[0]
    } else
        throw RuntimeException("No token passed")
    return token
}

fun sendGet(target: String, key: String) {
    val url = URL(target)

    with(url.openConnection() as HttpURLConnection) {
        requestMethod = "GET"
        setRequestProperty("Authorization", "token: $key")

        if (responseCode != 200)
            throw RuntimeException("HTTP $requestMethod returned code $responseCode.")
        else
            println("\n$requestMethod: $url")

        inputStream.bufferedReader().use {
            it.lines().forEach { line ->
                println(line)
            }
        }
    }
}