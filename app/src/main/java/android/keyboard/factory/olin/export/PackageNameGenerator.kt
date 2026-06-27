package android.keyboard.factory.olin.export

/** Deterministic applicationId derived from the project's name and id, so it never needs to be
 * regenerated (a stable id is required to overwrite-install on re-export).
 *
 * Android package name segments must be plain ASCII (letter/digit/underscore, starting with a
 * letter), but project names are free-form Unicode (e.g. Japanese). Rather than collapsing
 * every non-ASCII character to '_' — which throws away all distinguishing information and makes
 * every such name look identical — the name's raw UTF-8 bytes are hex-encoded into the package
 * segment. That's lossless (within [MAX_HEX_LENGTH]) and trivially satisfies the ASCII rule,
 * while the trailing project id is still what actually guarantees uniqueness when two projects
 * share the same name. The display name itself (the app label, set separately) keeps full
 * Unicode untouched. */
object PackageNameGenerator {
    private const val MAX_HEX_LENGTH = 64

    fun generate(projectId: Long, name: String): String {
        val hex = name.toByteArray(Charsets.UTF_8)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(MAX_HEX_LENGTH)
        return "android.keyboard.generated.k$hex$projectId"
    }
}
