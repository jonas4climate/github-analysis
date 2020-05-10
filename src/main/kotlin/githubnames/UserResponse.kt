package githubnames

data class UserResponse(
        val login: String
) {
    override fun toString(): String {
        return "User: $login"
    }
}