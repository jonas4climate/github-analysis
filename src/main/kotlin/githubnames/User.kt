package githubnames

data class User(
        val login: String,
        val language: String
) {
    override fun toString(): String {
        return "User $login using $language"
    }
}