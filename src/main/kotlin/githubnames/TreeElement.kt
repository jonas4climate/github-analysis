package githubnames

data class TreeElement(
        val path: String,
        val type: String
) {
    override fun toString(): String {
        return when(type) {
            "blob" -> "File: $path"
            "tree" -> "Folder: $path"
            else -> "TreeElement: $path"
        }
    }
}