package githubnames

class Config(
        val token: String,
        val verbose: Boolean,
        val startID: Int,
        val endID: Int,
        val mostUsed: Int
) {
    override fun toString(): String {
        return "Configuration: (token hidden), verbose=$verbose, startId=$startID, endId=$endID, mostUsed=$mostUsed"
    }
}