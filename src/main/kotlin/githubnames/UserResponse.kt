package githubnames

data class UserResponse(
        val id: Int,
        val login: String
) {
    override fun toString(): String {
        return "User: $login"
    }
}