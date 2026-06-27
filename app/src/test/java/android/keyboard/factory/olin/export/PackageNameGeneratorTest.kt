package android.keyboard.factory.olin.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNameGeneratorTest {

    private val validSegment = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

    @Test
    fun `ascii name produces a valid all-ascii package name`() {
        val packageName = PackageNameGenerator.generate(1L, "My Keyboard")
        assertTrue(packageName.split(".").all { validSegment.matches(it) })
    }

    @Test
    fun `japanese name produces a valid all-ascii package name`() {
        val packageName = PackageNameGenerator.generate(1L, "テスト")
        assertTrue(packageName.split(".").all { validSegment.matches(it) })
    }

    @Test
    fun `different japanese names with the same project id still differ`() {
        // Same id means any difference must come from the name encoding, not the id suffix --
        // i.e. names aren't collapsed into one fixed placeholder string.
        val a = PackageNameGenerator.generate(1L, "あいう")
        val b = PackageNameGenerator.generate(1L, "かきく")
        assertNotEquals(a, b)
    }

    @Test
    fun `same name with different project ids never collides`() {
        val a = PackageNameGenerator.generate(1L, "テスト")
        val b = PackageNameGenerator.generate(2L, "テスト")
        assertNotEquals(a, b)
    }

    @Test
    fun `is deterministic for the same inputs`() {
        assertEquals(PackageNameGenerator.generate(7L, "Foo"), PackageNameGenerator.generate(7L, "Foo"))
    }
}
