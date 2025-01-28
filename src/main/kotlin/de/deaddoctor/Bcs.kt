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
    inline fun <reified T> encodeToBytes(value: T) = encodeToBytes(serializer(), value)

    fun <T> encodeToBytes(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val encoder = BcsEncoder()
        encoder.encodeSerializableValue(serializer, value)
        return encoder.bytes.toByteString().toByteArray()
    }

    inline fun <reified T> decodeFromBytes(bytes: ByteArray): T = decodeFromBytes(serializer(), bytes)

    fun <T> decodeFromBytes(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        val decoder = BcsDecoder(BcsDecoder.InputBuffer(bytes))
        return decoder.decodeSerializableValue(deserializer)
    }

    const val TOP_BIT = 1 shl 7
    const val OTHER_BITS = TOP_BIT - 1

    class BcsEncoder : Encoder, CompositeEncoder {
        override val serializersModule = EmptySerializersModule()

        val bytes = ByteStringBuilder()

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

    class BcsDecoder(private val buffer: InputBuffer) : Decoder, CompositeDecoder {
        override val serializersModule = EmptySerializersModule()

        private var elementIndex = 0
        private var elementsCount = -1

        private fun decodeULEB128(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val current = buffer.readByte().toInt()
                val value = current and OTHER_BITS

                result = (value shl shift) or result
                shift += 7

                if (current and TOP_BIT == 0) return result
            }
        }

        // Boolean
        override fun decodeBoolean(): Boolean {
            if (buffer.remaining < 1) throw SerializationException("Tried to decode byte but reached EOF.")

            return when (val byte = buffer.readByte().toInt()) {
                0 -> false
                1 -> true
                else -> throw SerializationException("Tried to decode boolean but got '${byte}'.")
            }
        }

        // Integral numbers
        override fun decodeByte(): Byte {
            if (buffer.remaining < 1) throw SerializationException("Tried to decode byte but reached EOF.")

            return buffer.readByte()
        }

        override fun decodeShort(): Short {
            TODO("Not yet implemented")
        }

        override fun decodeInt(): Int {
            TODO("Not yet implemented")
        }

        override fun decodeLong(): Long {
            TODO("Not yet implemented")
        }

        // Floating-point numbers
        override fun decodeFloat(): Float {
            TODO("Not yet implemented")
        }

        override fun decodeDouble(): Double {
            TODO("Not yet implemented")
        }

        // Text
        override fun decodeChar(): Char {
            TODO("Not yet implemented")
        }

        override fun decodeString(): String {
            val length = decodeULEB128()
            if (buffer.remaining < length) throw SerializationException("Tried to decode string with length '${length}' but reached EOF.")
            return String(buffer.getBytes(length))
        }

        // Specials
        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
            TODO("Not yet implemented")
        }

        @ExperimentalSerializationApi
        override fun decodeNull(): Nothing? {
            TODO("Not yet implemented")
        }

        @ExperimentalSerializationApi
        override fun decodeNotNullMark(): Boolean {
            TODO("Not yet implemented")
        }

        // Complicated?
        override fun decodeInline(descriptor: SerialDescriptor): Decoder {
            TODO("Not yet implemented")
        }

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            return BcsDecoder(buffer)
        }

        // CompositeDecoder
        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            if (elementIndex == elementsCount) return CompositeDecoder.DECODE_DONE
            return elementIndex++
        }

        override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int) = decodeBoolean()

        override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) = decodeByte()

        override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) = decodeChar()

        override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) = decodeDouble()

        override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) = decodeFloat()

        override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) = decodeInline(descriptor)

        override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) = decodeInt()

        override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) = decodeLong()

        override fun <T> decodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: DeserializationStrategy<T>,
            previousValue: T?
        ) = decodeSerializableValue(deserializer)

        @ExperimentalSerializationApi
        override fun <T : Any> decodeNullableSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: DeserializationStrategy<T?>,
            previousValue: T?
        ): T? {
            TODO("Not yet implemented")
        }

        override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) = decodeShort()

        override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) = decodeString()

        override fun endStructure(descriptor: SerialDescriptor) {}

        @ExperimentalSerializationApi
        override fun decodeSequentially() = true

        override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
            elementsCount = decodeULEB128()
            return elementsCount
        }

        class InputBuffer(private val bytes: ByteArray) {
            var i = 0

            val remaining
                get() = bytes.size - i

            fun readByte() = bytes[i++]

            fun getBytes(count: Int): ByteArray {
                val bytes = bytes.copyOfRange(i, i + count)
                i += count
                return bytes
            }
        }
    }
}