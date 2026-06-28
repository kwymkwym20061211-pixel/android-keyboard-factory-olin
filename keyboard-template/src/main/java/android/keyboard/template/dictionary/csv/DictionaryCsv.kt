package android.keyboard.template.dictionary.csv

/** Custom CSV-like format for a dictionary: one `reading,target` pair per line, UTF-8, with a
 * backslash escape for literal `,`/`\` inside a field (`\,`, `\\`). Any other character after a
 * `\` is also taken literally (the backslash is simply dropped) so the escape rule is uniform;
 * a trailing lone `\` is a format error. This escaping only matters for the CSV transport — the
 * in-app DB/UI always stores and edits the raw, unescaped strings. */
object DictionaryCsv {

    sealed class ParseResult {
        data class Success(val rows: List<Pair<String, String>>) : ParseResult()
        data class Error(val lineNumber: Int, val reason: CsvErrorReason) : ParseResult()
    }

    /** Cause of a [ParseResult.Error], kept free of any user-facing text so the UI layer can
     * resolve it to a localized string. */
    sealed class CsvErrorReason {
        object TrailingBackslash : CsvErrorReason()
        data class WrongColumnCount(val count: Int) : CsvErrorReason()
        object EmptyField : CsvErrorReason()
    }

    private class TrailingBackslashException : Exception()

    private val INVALID_FILE_NAME_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

    fun encode(rows: List<Pair<String, String>>): String =
        rows.joinToString("\n") { (reading, target) -> "${escapeField(reading)},${escapeField(target)}" }

    fun decode(content: String): ParseResult {
        val rows = mutableListOf<Pair<String, String>>()
        for ((index, line) in content.lines().withIndex()) {
            if (line.isEmpty()) continue
            val lineNumber = index + 1
            val fields = try {
                splitEscaped(line)
            } catch (e: TrailingBackslashException) {
                return ParseResult.Error(lineNumber, CsvErrorReason.TrailingBackslash)
            }
            if (fields.size != 2) {
                return ParseResult.Error(lineNumber, CsvErrorReason.WrongColumnCount(fields.size))
            }
            val (reading, target) = fields
            if (reading.isEmpty() || target.isEmpty()) {
                return ParseResult.Error(lineNumber, CsvErrorReason.EmptyField)
            }
            rows.add(reading to target)
        }
        return ParseResult.Success(rows)
    }

    /** filename minus its `.csv` extension (case-insensitive), or null if it doesn't end with
     * `.csv` or the remaining name would be blank. */
    fun dictionaryNameFromFileName(fileName: String): String? {
        if (!fileName.lowercase().endsWith(".csv")) return null
        val name = fileName.substring(0, fileName.length - 4)
        return name.ifBlank { null }
    }

    /** First character in [name] that can't be used in a filename (since a dictionary name
     * becomes `<name>.csv` on export), or null if [name] is safe. Checks the union of
     * Windows' and POSIX's forbidden characters plus control characters, since exported CSVs
     * may end up read on either OS. */
    fun invalidFileNameChar(name: String): Char? =
        name.firstOrNull { it in INVALID_FILE_NAME_CHARS || it.code < 0x20 }

    private fun splitEscaped(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '\\' -> {
                    if (i + 1 >= line.length) throw TrailingBackslashException()
                    current.append(line[i + 1])
                    i += 2
                }
                ',' -> {
                    fields.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        fields.add(current.toString())
        return fields
    }

    private fun escapeField(value: String): String {
        val sb = StringBuilder()
        for (c in value) {
            if (c == '\\' || c == ',') sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }
}
