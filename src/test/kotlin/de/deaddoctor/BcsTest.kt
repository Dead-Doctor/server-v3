package de.deaddoctor

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BcsTest {

    @Serializable
    data class MyStruct(
        val boolean: Boolean,
        val bytes: List<Byte>,
        val label: String
    )

    @Serializable
    data class Wrapper(
        val inner: MyStruct,
        val name: String
    )

    private val s = MyStruct(
        true,
        listOf(0xC0.toByte(), 0xDE.toByte()),
        "a"
    )
    private val sBytes = byteArrayOf(1, 2, 0xC0.toByte(), 0xDE.toByte(), 1, 'a'.code.toByte())

    private val w = Wrapper(
        s,
        "b"
    )
    private val wBytes = byteArrayOf(1, 2, 0xC0.toByte(), 0xDE.toByte(), 1, 'a'.code.toByte(), 1, 'b'.code.toByte())

    @Test
    fun encodeToBytes() {
        val sEncoded = Bcs.encodeToBytes(s)
        val wEncoded = Bcs.encodeToBytes(w)
        assertContentEquals(sBytes, sEncoded)
        assertContentEquals(wBytes, wEncoded)
    }

    @Test
    fun decodeFromBytes() {
        val sDecoded = Bcs.decodeFromBytes<MyStruct>(sBytes)
        val wDecoded = Bcs.decodeFromBytes<Wrapper>(wBytes)
        assertEquals(s, sDecoded)
        assertEquals(w, wDecoded)
    }

    @Test
    fun unsignedIntegers() {
        val value = 1987
        val encoded = Bcs.encodeToBytes(value)
        val decoded = Bcs.decodeFromBytes<Int>(encoded)
        assertEquals(value, decoded)
    }
}