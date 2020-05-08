package githubnames

import com.beust.klaxon.Klaxon
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class App {
    val greeting: String
        get() {
            return "Hello world."
        }
}

const val GITHUB = "https://api.github.com"
val klaxon = Klaxon()

fun main(args: Array<String>) {
    val token = getToken(args)
    var answer: String = makeRequest("GET", "/users", token, true)
    val users: List<User> = klaxon.parseArray(answer) ?: throw RuntimeException("Could not fetch users")

    // TODO final
    /*users.forEach {
        answer = makeRequest("GET", "/users/${it.login}/repos", token, false)
    }*/
    // TODO temp
    answer = makeRequest("GET", "/users/${users[0].login}/repos", token, true)

    val repos: List<Repo> = klaxon.parseArray(answer) ?: throw RuntimeException("Could not fetch repos")
}

fun makeRequest(type: String, target: String, token: String, verbose: Boolean = false): String {
    val url = URL(GITHUB + target)

    with(url.openConnection() as HttpURLConnection) {
        requestMethod = type
        setRequestProperty("Authorization", "token $token")

        if (responseCode != 200) {
            if (responseCode == 401)
                throw RuntimeException("Please ensure a valid API token is passed")
            else
                throw RuntimeException("HTTP request $requestMethod returned code $responseCode")
        }
        assert(responseCode != 401) { print("Please ensure your a valid API token is passed") }
        assert(responseCode == 200) { print("HTTP request $requestMethod returned code $responseCode") }

        val requestsLeft = headerFields["X-RateLimit-Remaining"].toString().removeSurrounding("[", "]").toLong()

        if (requestsLeft < 1) { //TODO compare == 0
            val resetTime = headerFields["X-RateLimit-Reset"].toString().removeSurrounding("[", "]").toLong()
            // System returned time is not reliable enough but one minute room for error prevents making requests
            val timeToReset = resetTime - (System.currentTimeMillis() / 1000) + 60
            println("Depleted API limit, waiting ${timeToReset}s")
            Thread.sleep(1000*timeToReset)
        }

        val answer = inputStream.bufferedReader().use(BufferedReader::readText)

        if (verbose) {
            println("\n$requestMethod: $url")
            //println("Requests left: ${headerFields["X-RateLimit-Remaining"]}")
            //println("Entire Header: $headerFields")
            //println(answer)
        }

        return answer
    }
}

fun getToken(args: Array<String>): String {
    val file = File(".github-oauth.token")
    return if (file.exists() && file.canRead() && file.readText() != "") {
        file.readText()
    } else if (args.size == 1) {
        args[0]
    } else
        ""
}