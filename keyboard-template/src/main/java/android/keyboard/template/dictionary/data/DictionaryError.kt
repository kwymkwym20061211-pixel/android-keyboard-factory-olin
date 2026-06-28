package android.keyboard.template.dictionary.data

import android.keyboard.template.dictionary.csv.DictionaryCsv

/** Validation/import failure from [DictionaryRepository], kept free of any user-facing text so
 * the UI layer can resolve it to a localized string. */
sealed class DictionaryError : Exception() {
    object EmptyName : DictionaryError()
    data class InvalidNameChar(val char: Char) : DictionaryError()
    data class DuplicateName(val name: String) : DictionaryError()
    object EmptyWordFields : DictionaryError()
    object InvalidCsvFileName : DictionaryError()
    data class CsvParseError(val lineNumber: Int, val reason: DictionaryCsv.CsvErrorReason) : DictionaryError()
}
