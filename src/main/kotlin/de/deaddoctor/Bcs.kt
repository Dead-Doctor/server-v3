package de.deaddoctor

import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule

object Bcs {
    inline fun <reified T> encodeToByteArray(value: T) = encodeToByteArray(serializer(), value)

    fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val encoder = BcsEncoder()
        encoder.encodeSerializableValue(serializer, value)
        return encoder.bytes.toByteString().toByteArray()
    }

    inline fun <reified T> decodeFromBytes(bytes: ByteArray): T = decodeFromBytes(serializer(), bytes)

    fun <T> decodeFromBytes(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        val decoder = BcsDecoder(bytes)
        return decoder.decodeSerializableValue(deserializer)
    }

    init {
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

        val s = MyStruct(
            true,
            listOf(0xC0.toByte(), 0xDE.toByte() ),
            "a"
        )
        val w = Wrapper(
            s,
            "b"
        )

        val sBytes = encodeToByteArray(s)
        val wBytes = encodeToByteArray(w)

        println(sBytes.joinToString { it.toUByte().toString(16) })
        println(wBytes.joinToString { it.toUByte().toString(16) })

        val sDecoded = decodeFromBytes<MyStruct>(sBytes)
    }

    class BcsEncoder : Encoder, CompositeEncoder {
        override val serializersModule = EmptySerializersModule()

        val bytes = ByteStringBuilder()

        companion object {
            const val TOP_BIT = 1 shl 7
            const val OTHER_BITS = TOP_BIT - 1
        }

        private fun encodeULEB128(value: Int) {
            var remaining = value
            while (remaining > OTHER_BITS) {
                val current = remaining and OTHER_BITS
                bytes.append((current or TOP_BIT).toByte())
                remaining = remaining ushr 7
            }
            bytes.append(remaining.toByte())
        }

        // Boolean
        override fun encodeBoolean(value: Boolean) {
            bytes.append(if (value) 1 else 0)
        }

        // Integral numbers
        override fun encodeByte(value: Byte) {
            bytes.append(value)
        }

        override fun encodeShort(value: Short) {
            TODO("Not yet implemented")
        }

        override fun encodeInt(value: Int) {
            for (i in 0..<4) {
                bytes.append((value ushr (i * 8)).toByte())
            }
        }

        override fun encodeLong(value: Long) {
            TODO("Not yet implemented")
        }

        // Floating-point numbers
        override fun encodeFloat(value: Float) {
            TODO("Not yet implemented")
        }

        override fun encodeDouble(value: Double) {
            TODO("Not yet implemented")
        }

        // Text
        override fun encodeChar(value: Char) {
            TODO("Not yet implemented")
        }

        override fun encodeString(value: String) {
            encodeULEB128(value.length)
            bytes.append(value.toByteArray())
        }

        // Specials
        override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
            TODO("Not yet implemented")
        }

        @ExperimentalSerializationApi
        override fun encodeNull() {
            bytes.append(0)
        }

        @ExperimentalSerializationApi
        override fun encodeNotNullMark() {
            bytes.append(1)
        }

        // Complicated?
        override fun encodeInline(descriptor: SerialDescriptor): Encoder {
            TODO("Not yet implemented")
        }

        override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
            encodeULEB128(collectionSize)
            return this
        }

        override fun beginStructure(descriptor: SerialDescriptor) = this

        override fun endStructure(descriptor: SerialDescriptor) {}

        // CompositeEncoder
        override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
            encodeBoolean(value)
        }

        override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
            TODO("Not yet implemented")
        }

        override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
            TODO("Not yet implemented")
        }

        override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
            TODO("Not yet implemented")
        }

        override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
            TODO("Not yet implemented")
        }

        override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
            TODO("Not yet implemented")
        }

        override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
            encodeInt(value)
        }

        override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
            TODO("Not yet implemented")
        }

        override fun <T> encodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T
        ) {
            encodeSerializableValue(serializer, value)
        }

        @ExperimentalSerializationApi
        override fun <T : Any> encodeNullableSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T?
        ) {
            encodeNullableSerializableValue(serializer, value)
        }

        override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
            TODO("Not yet implemented")
        }

        override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
            encodeString(value)
        }
    }

    class BcsDecoder(val bytes: ByteArray) : Decoder {
        override val serializersModule = EmptySerializersModule()

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            TODO("Not yet implemented")
        }

        override fun decodeBoolean(): Boolean {
            TODO("Not yet implemented")
        }

        override fun decodeByte(): Byte {
            TODO("Not yet implemented")
        }

        override fun decodeChar(): Char {
            TODO("Not yet implemented")
        }

        override fun decodeDouble(): Double {
            TODO("Not yet implemented")
        }

        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
            TODO("Not yet implemented")
        }

        override fun decodeFloat(): Float {
            TODO("Not yet implemented")
        }

        override fun decodeInline(descriptor: SerialDescriptor): Decoder {
            TODO("Not yet implemented")
        }

        override fun decodeInt(): Int {
            TODO("Not yet implemented")
        }

        override fun decodeLong(): Long {
            TODO("Not yet implemented")
        }

        @ExperimentalSerializationApi
        override fun decodeNotNullMark(): Boolean {
            TODO("Not yet implemented")
        }

        @ExperimentalSerializationApi
        override fun decodeNull(): Nothing? {
            TODO("Not yet implemented")
        }

        override fun decodeShort(): Short {
            TODO("Not yet implemented")
        }

        override fun decodeString(): String {
            TODO("Not yet implemented")
        }

    }
}