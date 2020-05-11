package githubnames

data class User(
        val id: Int,
        val login: String
) {
    override fun toString(): String {
        return "User: $login"
    }
}