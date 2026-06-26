package android.keyboard.factory.olin.export

/** Deterministic, collision-free applicationId derived from the project's own id, so it never
 * needs to be regenerated (a stable id is required to overwrite-install on re-export). */
object PackageNameGenerator {
    fun generate(projectId: Long, name: String): String {
        val sanitized = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .ifEmpty { "keyboard" }
            .let { if (it.first().isLetter()) it else "k$it" }
        return "android.keyboard.generated.$sanitized$projectId"
    }
}
