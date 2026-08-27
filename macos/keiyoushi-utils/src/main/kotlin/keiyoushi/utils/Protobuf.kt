package keiyoushi.utils

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Base64

@PublishedApi
internal val protoInstance: ProtoBuf by lazy { Injekt.get<ProtoBuf>() }

@PublishedApi
internal val PROTOBUF_MEDIA_TYPE = "application/protobuf".toMediaType()

/**
 * Decode a [ByteArray] as a Protobuf message into type [T].
 */
inline fun <reified T> ByteArray.decodeProto(
    deserializer: DeserializationStrategy<T> = serializer(),
): T = protoInstance.decodeFromByteArray(deserializer, this)

/**
 * Encode a Protobuf message to a [ByteArray].
 */
inline fun <reified T> T.encodeProto(
    serializerStrategy: SerializationStrategy<T> = serializer(),
): ByteArray = protoInstance.encodeToByteArray(serializerStrategy, this)

/**
 * Decode a Base64-encoded Protobuf string into type [T].
 */
inline fun <reified T> String.decodeProtoBase64(
    deserializer: DeserializationStrategy<T> = serializer(),
): T =
    Base64.getDecoder().decode(this).decodeProto(deserializer)

/**
 * Encode a Protobuf message as a Base64-encoded string.
 */
inline fun <reified T> T.encodeProtoBase64(
    serializerStrategy: SerializationStrategy<T> = serializer(),
): String =
    Base64.getEncoder().encodeToString(this.encodeProto(serializerStrategy))

/**
 * Parse an [okhttp3.Response] body as Protobuf, handling the response lifecycle.
 */
inline fun <reified T> Response.parseAsProto(
    crossinline transform: (ByteArray) -> ByteArray = { it },
    deserializer: DeserializationStrategy<T> = serializer(),
): T {
    val bytes = body?.bytes() ?: throw IllegalStateException("Response body is null")
    return transform(bytes).decodeProto(deserializer)
}

/**
 * Parse a [ResponseBody] as Protobuf.
 */
inline fun <reified T> ResponseBody.parseAsProto(
    crossinline transform: (ByteArray) -> ByteArray = { it },
    deserializer: DeserializationStrategy<T> = serializer(),
): T {
    val bytes = bytes()
    return transform(bytes).decodeProto(deserializer)
}

/**
 * Convert a Protobuf-encodable object to an [okhttp3.RequestBody] with the protobuf media type.
 */
inline fun <reified T> T.toRequestBodyProto(
    serializerStrategy: SerializationStrategy<T> = serializer(),
    contentType: okhttp3.MediaType = PROTOBUF_MEDIA_TYPE,
): okhttp3.RequestBody = encodeProto(serializerStrategy).toRequestBody(contentType)
