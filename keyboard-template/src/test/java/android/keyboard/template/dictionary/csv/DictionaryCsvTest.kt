package android.keyboard.template.dictionary.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryCsvTest {

    @Test
    fun `encode then decode round-trips plain rows`() {
        val rows = listOf("あ" to "亜", "い" to "意")
        val decoded = DictionaryCsv.decode(DictionaryCsv.encode(rows))
        assertEquals(DictionaryCsv.ParseResult.Success(rows), decoded)
    }

    @Test
    fun `encode escapes comma and decode recovers it`() {
        val rows = listOf("かんま" to "a,b")
        val encoded = DictionaryCsv.encode(rows)
        assertEquals("かんま,a\\,b", encoded)
        assertEquals(DictionaryCsv.ParseResult.Success(rows), DictionaryCsv.decode(encoded))
    }

    @Test
    fun `encode escapes backslash and decode recovers it`() {
        val rows = listOf("えん" to "a\\b")
        val encoded = DictionaryCsv.encode(rows)
        assertEquals("えん,a\\\\b", encoded)
        assertEquals(DictionaryCsv.ParseResult.Success(rows), DictionaryCsv.decode(encoded))
    }

    @Test
    fun `decode treats any character after backslash as literal`() {
        // \w in the source is just an escaped 'w', i.e. equivalent to a plain 'w'.
        val result = DictionaryCsv.decode("read,ta\\wget")
        assertEquals(DictionaryCsv.ParseResult.Success(listOf("read" to "tawget")), result)
    }

    @Test
    fun `decode reports trailing lone backslash as an error`() {
        val result = DictionaryCsv.decode("read,target\\")
        assertTrue(result is DictionaryCsv.ParseResult.Error)
        assertEquals(1, (result as DictionaryCsv.ParseResult.Error).lineNumber)
    }

    @Test
    fun `decode reports wrong field count as an error with line number`() {
        val result = DictionaryCsv.decode("a,b\na,b,c")
        assertTrue(result is DictionaryCsv.ParseResult.Error)
        assertEquals(2, (result as DictionaryCsv.ParseResult.Error).lineNumber)
    }

    @Test
    fun `decode reports empty field as an error`() {
        val result = DictionaryCsv.decode("読み,")
        assertTrue(result is DictionaryCsv.ParseResult.Error)
    }

    @Test
    fun `decode skips blank lines`() {
        val result = DictionaryCsv.decode("a,b\n\nc,d\n")
        assertEquals(DictionaryCsv.ParseResult.Success(listOf("a" to "b", "c" to "d")), result)
    }

    @Test
    fun `dictionaryNameFromFileName strips csv extension case-insensitively`() {
        assertEquals("こんにちは", DictionaryCsv.dictionaryNameFromFileName("こんにちは.csv"))
        assertEquals("foo", DictionaryCsv.dictionaryNameFromFileName("foo.CSV"))
    }

    @Test
    fun `dictionaryNameFromFileName rejects non-csv or blank-after-strip names`() {
        assertNull(DictionaryCsv.dictionaryNameFromFileName("foo.txt"))
        assertNull(DictionaryCsv.dictionaryNameFromFileName(".csv"))
    }

    @Test
    fun `invalidFileNameChar detects forbidden characters`() {
        assertEquals('/', DictionaryCsv.invalidFileNameChar("a/b"))
        assertEquals(':', DictionaryCsv.invalidFileNameChar("a:b"))
    }

    @Test
    fun `invalidFileNameChar returns null for an ordinary name`() {
        assertNull(DictionaryCsv.invalidFileNameChar("こんにちは辞書"))
    }
}
