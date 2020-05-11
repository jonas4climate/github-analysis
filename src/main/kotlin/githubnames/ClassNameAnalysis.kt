package githubnames

import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.HashMap

const val GITHUB_API = "https://api.github.com"
//const val GITHUB = "https://github.com" // INFO: use when git pulling repos instead
val classNameCounts = HashMap<String, Int>(1_000_000)
val klaxon = Klaxon()


fun main() {
    // Get setup via the default config file
    val config: Config = readConfig()
    // Perform analysis
    analyze(config.token, config.verbose, endID = config.endID, mostUsed = config.mostUsed)
}

/**
 * Main function performing the analysis of Java class names of GitHub-hosted projects
 *
 * @param token GitHub API token required for authentication
 * @param verbose Whether to print verbosely during execution
 * @param startID GitHub user ID the scan starts at, for full scan should be default
 * @param endID GitHub user ID the scan ends at, for full scan should be default
 * @param mostUsed Number of class names to categorize and additionally export in a most-used.csv file
 */
fun analyze(token: String, verbose: Boolean, startID: Int = 0, endID: Int = Int.MAX_VALUE, mostUsed: Int = 20) {
    // Track id of last user to determine user ID to start fetching users from at the next iteration
    var lastUserId = startID
    try {
        // As long as there are users being fetched, keep going
        do {
            // Fetch a set of user information
            val entries = minOf(100, endID - lastUserId)
            var url = "$GITHUB_API/users?since=$lastUserId&per_page=$entries"
            var response = makeHTTPRequest("GET", url, token, verbose)
                    ?: throw RuntimeException("Request failed with 404 error")

            // Parse user JSON into User List
            val users: List<User> = klaxon.parseArray(response) ?: throw RuntimeException("Could not fetch users")
            lastUserId = users.last().id


            users.forEach { user ->
                // Get repository information from this user
                url = "$GITHUB_API/users/${user.login}/repos"
                response = makeHTTPRequest("GET", url, token, verbose)
                        ?: throw RuntimeException("Request failed with 404 error")

                // Parse repository JSO into List of repositories
                val repos: List<Repo> = klaxon.parseArray(response) ?: throw RuntimeException("Could not fetch repos")

                // Ignore those repositories who are not Java projects (INFO: could search for java files anyways but will be more efficient this way)
                val javaRepos = repos.filter { it.language == "Java" }

                /* INFO: Alternative concept: Download repository instead of making API calls, maybe faster if parsing files for class names
                    (more local computation and network bandwidth requirements but less time waiting for API call responses)

                javaRepos.forEach {repo ->
                    // Download repo instead of making many API calls to reduce time waiting for responses and increase speed by performing operations locally
                    val command = "git clone --depth 1 $GITHUB/${user.login}/${repo.name} temp/${repo.name}"
                    Runtime.getRuntime().exec(command)
                }*/

                javaRepos.forEach java@{ javaRepo ->
                    // Get list of all files and folders in the repository ("ignores" depth in the tree, all listed)
                    url = "$GITHUB_API/repos/${user.login}/${javaRepo.name}/git/trees/master?recursive=true"
                    response = makeHTTPRequest("GET", url, token, verbose) ?: return@java

                    // Ignore data around list of files and folders to save unnecessary parsing
                    val tree = response.substring(response.indexOf('['), response.lastIndexOf(']') + 1)

                    // Parse into List of files and folders
                    val treeElements: List<TreeElement>? = klaxon.parseArray(tree)
                            ?: throw RuntimeException("Could not fetch git tree elements")

                    // Ignore folders (only care about files)
                    val files = treeElements?.filter { it.type == "blob" }

                    files?.forEach { file ->
                        // Check if java file
                        if (file.path.length > 5 && file.path.takeLast(5) == ".java") {
                            var className = file.path
                            // Remove folders before java file name
                            if ('/' in className)
                                className = className.drop(className.lastIndexOf('/') + 1)

                            // Remove extension and capitalize first letter
                            // (some files may be written LowerCamelCase but code usually contains UpperCamelCase class names)
                            className = className.dropLast(5).capitalize()

                            // INFO: Assume file name to be class name to greatly reduce parsing computation,
                            //  otherwise consider prior alternative approach using `git clone`

                            // ignore package-info.java file which don't contain classes
                            if (className != "Package-info") {
                                // Increase count of this file name by one
                                classNameCounts[className] = classNameCounts.getOrDefault(className, 0) + 1
                            }
                        }
                    }
                }
            }
            // Print current status if needed
            //printSortedStatus(classNameCounts, mostUsed)
        } while (users.isNotEmpty() && lastUserId < endID)
    } finally {
        // Write results sorted to csv file
        exportToCSV(classNameCounts, mostUsed)
    }
}

/**
 * Function exporting the entire list of class names with occurrences as CSV and an
 * additional second file with just the specified number of most used class names
 *
 * @param classNameCounts Map mapping from class names to occurrences
 * @param mostUsed number of items counted to the most used list for the second CSV file
 */
fun exportToCSV(classNameCounts: HashMap<String, Int>, mostUsed: Int) {
    // Ensure safe file creation/opening
    val out = File("results/all-names.csv").also { file -> file.parentFile.mkdirs() }
    // Column names
    out.writeText("class name, occurrences\n")
    // Append class names sorted by occurrences in CSV style
    val sortedList = classNameCounts.toList().sortedByDescending { (_, value) -> value }
    sortedList.forEach { (name, n) -> out.appendText("$name, $n\n")}

    // Take mostUsed many of the most used class names (descending with index increase)
    val mostUsedList = sortedList.take(mostUsed)
    // Print to terminal
    println(mostUsedList)
    // Export to file similar to before
    val out2 = File("results/most-used.csv")
    out2.writeText("class name, occurrences\n")
    mostUsedList.forEach { (name, n) -> out2.appendText("$name, $n\n")}
}

/**
 * Reading and verifying the configuration options for the analysis tool from the passed config file
 *
 * @param path The path to the config JSON file
 * @return Configuration settings
 */
fun readConfig(path: String = ".config.json"): Config {
    try {
        val config = klaxon.parse<Config>(File(path)) ?: throw RuntimeException("Could not parse from config file")
        when {
            config.token == "" ->
                throw IllegalArgumentException("Ensure a token for the API is passed, otherwise it will not be functional")
            config.startID < 0 || config.startID > Int.MAX_VALUE ->
                throw IllegalArgumentException("startID only allows positive Integer values, for default set to 0")
            config.endID < 1 || config.endID > Int.MAX_VALUE ->
                throw IllegalArgumentException("endID only allows positive non-zero Integer values, for default set to 2^32 - 1")
            config.mostUsed < 1 ->
                throw IllegalArgumentException("mostUsed only allows positive non-zero Integer values")
        }
        return config
    } catch (e: KlaxonException) {
        throw IllegalArgumentException("The JSON was invalid and parsing failed, please check the .config.json for completion. " +
                "It requires all fields from the githubnames.Config class")
    }
}

/**
 * Helper method to print a given number of most used classes during runtime of the analysis
 *
 * @param classNameCounts Map mapping class names to occurrences
 * @param mostUsed number of most used class names to display
 */
fun printSortedStatus(classNameCounts: HashMap<String, Int>, mostUsed: Int) {
    println(classNameCounts.toList().sortedByDescending { (_, value) -> value }.take(mostUsed).toList())
}

/**
 * Sending a HTTP method to the specified URL, authenticating with the token and returning the data received
 *
 * @param type HTTP method
 * @param target URL to send packet to
 * @param token GitHub API token for OAuth
 * @param verbose Whether to print details during execution
 */
fun makeHTTPRequest(type: String, target: String, token: String, verbose: Boolean = false): String? {
    // Set URL to send request to
    val url = URL(target)

    // Send and receive
    with(url.openConnection() as HttpURLConnection) {
        // Type of request (here always GET)
        requestMethod = type
        // Authorization for GitHub API OAuth
        setRequestProperty("Authorization", "token $token")

        // Handle return code and inform user intelligently of causes
        when (responseCode) {
            401 -> throw RuntimeException("Please ensure a valid API token is passed in the configuration file")
            404 -> return null // INFO: Rarely happens when no master branch exists, ignoring these cases for now
            !in listOf(200) -> throw RuntimeException("HTTP request $requestMethod returned code $responseCode")
        }

        // Get number of requests still available
        val requestsLeft = headerFields["X-RateLimit-Remaining"].toString().removeSurrounding("[", "]").toLong()

        // Let current Thread sleep until requests are available again
        if (requestsLeft < 1) {
            val resetTime = headerFields["X-RateLimit-Reset"].toString().removeSurrounding("[", "]").toLong()
            // INFO: System returned time is not reliable enough so one minute additional margin prevents making requests too early
            // Determine time to sleep
            val timeToReset = resetTime - (System.currentTimeMillis() / 1000) + 60
            println("Depleted API limit, waiting ${timeToReset}s")
            Thread.sleep(1000*timeToReset)
        }

        // If verbose print the request being made and API calls left until depletion and Thread sleep
        if (verbose)
            println("$requestMethod: $url, $requestsLeft API calls left")

        // Return server JSON response
        return inputStream.bufferedReader().use(BufferedReader::readText)
    }
}