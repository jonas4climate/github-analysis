package githubnames

import com.beust.klaxon.Klaxon
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.HashMap

class App {
    val greeting: String
        get() {
            return "Hello world."
        }
}

const val GITHUB_API = "https://api.github.com"
//const val GITHUB = "https://github.com"
val classNameCounts = HashMap<String, Int>(1000000)
val klaxon = Klaxon()

fun main(args: Array<String>) {
    val token = getToken(args)

    var lastUserId = 0
    do {
        var response = makeHTTPRequest("GET", "$GITHUB_API/users?since=$lastUserId", token, true)
        val users: List<User> = klaxon.parseArray(response) ?: throw RuntimeException("Could not fetch users")
        lastUserId = users.last().id

        users.forEach { user ->
            response = makeHTTPRequest("GET", "$GITHUB_API/users/${user.login}/repos", token, true)

            val repos: List<Repo> = klaxon.parseArray(response) ?: throw RuntimeException("Could not fetch repos")
            val javaRepos = repos.filter { it.language == "Java" }

            /* INFO: Alternative concept: Download repository instead of API calls
            javaRepos.forEach {repo ->
                // Download repo instead of making many API calls to reduce time waiting for responses and increase speed by performing operations locally
                val command = "git clone --depth 1 $GITHUB/${user.login}/${repo.name} temp/${repo.name}"
                println(command)
                Runtime.getRuntime().exec(command)
            }*/

            javaRepos.forEach { javaRepo ->
                response = makeHTTPRequest("GET", "$GITHUB_API/repos/${user.login}/${javaRepo.name}/git/trees/master?recursive=true", token, true)
                response = response.substring(response.indexOf('['), response.lastIndexOf(']') + 1)

                val treeElements: List<TreeElement>? = klaxon.parseArray(response) ?: throw RuntimeException("Could not fetch git tree elements")
                val files = treeElements?.filter { it.type == "blob" }

                files?.forEach { file ->
                    // 6 is minimum size for file name of java file (e.g. x.java)
                    if (file.path.length > 5 && file.path.takeLast(5) == ".java") {
                        var className = file.path
                        if ('/' in className)
                            className = className.drop(className.lastIndexOf('/') + 1)
                        // Remove extension and capitalize first letter
                        // (some files may be written LowerCamelCase but code usually contains UpperCamelCase class names)
                        className = className.dropLast(5).capitalize()
                        classNameCounts[className] = classNameCounts.getOrDefault(className, 0) + 1
                    }
                }
            }
        }

        /*val significantSorted = classNameCounts.toList().sortedByDescending { (_, value) -> value }.take(20).toList()
        println(significantSorted)*/
    } while (users.isNotEmpty() && lastUserId < 1000)
    // Write to file as toString (TODO: CSV)
    File("results.txt").writeText(classNameCounts.toList().sortedByDescending { (_, value) -> value }.toString())
}

fun makeHTTPRequest(type: String, target: String, token: String, verbose: Boolean = false): String {
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