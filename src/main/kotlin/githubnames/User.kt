package githubnames

data class User(
    val login: String
) {
    override fun toString(): String {
        return "User: $login"
    }
}