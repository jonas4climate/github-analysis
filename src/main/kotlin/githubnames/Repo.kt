package githubnames

data class Repo(
        val name: String,
        val language: String?
) {
    override fun toString(): String {
        return "Repo: $name written in $language"
    }
}