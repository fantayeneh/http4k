package org.http4k.lens

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpMessage
import org.http4k.core.MultipartEntity
import org.http4k.core.MultipartFormBody
import org.http4k.core.with
import java.io.InputStream
import java.util.*

object MultipartFormField : BiDiLensSpec<MultipartForm, String, String>("form",
    ParamMeta.StringParam,
    LensGet { name, (fields) -> fields.getOrDefault(name, listOf()) },
    LensSet { name, values, target -> values.fold(target, { m, next -> m.plus(name to next) }) }
)

data class MultipartFormFile(val filename: String, val contentType: ContentType, val content: InputStream) {
    companion object : BiDiLensSpec<MultipartForm, MultipartFormFile, MultipartFormFile>("form",
        ParamMeta.FileParam,
        LensGet { name, form -> form.files[name]?.map { MultipartFormFile(it.filename, it.contentType, it.content) } ?: emptyList() },
        LensSet { name, values, target -> values.fold(target, { m, next -> m.plus(name to next) }) }
    )
}

data class MultipartForm(val fields: Map<String, List<String>> = emptyMap(),
                         val files: Map<String, List<MultipartFormFile>> = emptyMap(),
                         val errors: List<Failure> = emptyList()) {

    @JvmName("plusField")
    operator fun plus(kv: Pair<String, String>): MultipartForm =
        copy(fields = fields.plus(kv.first to fields.getOrDefault(kv.first, emptyList()).plus(kv.second)))

    @JvmName("plusFile")
    operator fun plus(kv: Pair<String, MultipartFormFile>): MultipartForm =
        copy(files = files.plus(kv.first to files.getOrDefault(kv.first, emptyList()).plus(kv.second)))
}

val MULTIPART_BOUNDARY = UUID.randomUUID().toString()

fun Body.Companion.multipartForm(validator: Validator, vararg parts: Lens<MultipartForm, *>, boundary: String = MULTIPART_BOUNDARY): BiDiBodyLensSpec<MultipartForm> =
    BiDiBodyLensSpec(parts.map { it.meta }, ContentType.MULTIPART_FORM_DATA,
        LensGet { _, target ->
            MultipartFormBody.from(target)
            listOf(MultipartFormBody.from(target).apply {
                ContentNegotiation.Strict(ContentType.MultipartFormWithBoundary(boundary), Header.Common.CONTENT_TYPE(target))
            })
        },
        LensSet { _: String, values: List<Body>, target: HttpMessage ->
            values.fold(target) { a, b ->
                a.body(b)
                    .with(Header.Common.CONTENT_TYPE of ContentType.MultipartFormWithBoundary(boundary))
            }
        })
        .map(Body::toMultipartForm, { it.toMultipartFormEntity(boundary) })
        .map({ it.copy(errors = validator(it, *parts)) }, { it.copy(errors = validator(it, *parts)) })

internal fun Body.toMultipartForm(): MultipartForm {
    return MultipartForm()
}

internal fun MultipartForm.toMultipartFormEntity(boundary: String): MultipartFormBody {
    val multipartFormBody = MultipartFormBody(boundary = boundary)
    val withFields = fields.toList().fold(multipartFormBody) { memo, (name, values) ->
        values.fold(memo) { memo2, next2 ->
            memo2.plus(MultipartEntity.Form(name, next2))
        }
    }

    return files.toList().fold(withFields) { memo, (name, values) ->
        values.fold(memo) { memo2, (filename, contentType, content) ->
            memo2.plus(MultipartEntity.File(name, filename, contentType, content))
        }
    }
}