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

const val GITHUB_API = "https://api.github.com"
const val GITHUB = "https://github.com"
val klaxon = Klaxon()

fun main(args: Array<String>) {
    val token = getToken(args)
    var lastUserId = 0

    do {
        var response = makeRequest("GET", "$GITHUB_API/users?since=$lastUserId", token, true)
        val userResponses: List<UserResponse> = klaxon.parseArray(response) ?: throw RuntimeException("Could not fetch users")
        lastUserId = userResponses.last().id

        userResponses.forEach { user ->
            response = makeRequest("GET", "$GITHUB_API/users/${user.login}/repos", token, true)
            val repoResponses: List<RepoResponse> = klaxon.parseArray(response) ?: throw RuntimeException("Could not fetch repos")
            val javaRepos = repoResponses.filter { it.language == "Java" }

            /* INFO: Download instead of API calls
            javaRepos.forEach {repo ->
                // Download repo instead of making many API calls to reduce time waiting for responses and increase speed by performing operations locally
                val command = "git clone $GITHUB/${user.login}/${repo.name} temp/${repo.name}"
                println(command)
                Runtime.getRuntime().exec(command)
            }*/

            javaRepos.forEach {repo ->
                response = makeRequest("GET", "$GITHUB_API/repos/${user.login}/${repo.name}/git/trees/master?recursive=true", token, true)
                response = response.substring(response.indexOf('['), response.indexOf(']')+1)
                val elements: List<ElementResponse>? = klaxon.parseArray(response)
            }
        }
    } while (userResponses.isNotEmpty())
}

fun makeRequest(type: String, target: String, token: String, verbose: Boolean = false): String {
    val url = URL(target)

    with(url.openConnection() as HttpURLConnection) {
        requestMethod = type
        setRequestProperty("Authorization", "token $token")

        if (responseCode == 401)
            throw RuntimeException("Please ensure a valid API token is passed")
        else if (responseCode != 200)
            throw RuntimeException("HTTP request $requestMethod returned code $responseCode")

        val requestsLeft = headerFields["X-RateLimit-Remaining"].toString().removeSurrounding("[", "]").toLong()

        if (requestsLeft < 1) {
            val resetTime = headerFields["X-RateLimit-Reset"].toString().removeSurrounding("[", "]").toLong()
            // System returned time is not reliable enough but one minute room for error prevents making requests
            val timeToReset = resetTime - (System.currentTimeMillis() / 1000) + 60
            println("Depleted API limit, waiting ${timeToReset}s")
            Thread.sleep(1000*timeToReset)
        }

        if (verbose)
            println("$requestMethod: $url, $requestsLeft API calls left")

        return inputStream.bufferedReader().use(BufferedReader::readText)
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