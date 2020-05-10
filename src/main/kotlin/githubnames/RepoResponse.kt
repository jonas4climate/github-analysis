package githubnames

data class RepoResponse(
        val name: String,
        val language: String?
) {
    override fun toString(): String {
        return "Repo: $name written in $language"
    }
}