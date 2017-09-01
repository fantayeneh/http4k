package org.http4k.multipart

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.http4k.multipart.part.Part
import org.http4k.multipart.part.StreamingPart
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*

class MultipartFormMapTest {

    @Test
    @Ignore
    fun example() {

        val maxStreamLength = 100000 // maximum length of the stream, will throw exception if this is exceeded
        val writeToDiskThreshold = 1024 // maximum length of in memory object - if part is bigger then write to disk
        val temporaryFileDirectory: File? = null // use default temporary file directory
        val contentType = "multipart/form-data; boundary=------WebKitFormBoundary6LmirFeqsyCQRtbj" // content type from HTTP header

        // you are responsible for closing the body InputStream
        try {
            FileInputStream("examples/safari-example.multipart").use { body ->

                val boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length).toByteArray(ISO_8859_1)
                val streamingParts = StreamingMultipartFormParts.parse(
                    boundary, body, ISO_8859_1, maxStreamLength)

                val parts = MultipartFormMap.formMap(streamingParts, UTF_8, writeToDiskThreshold, temporaryFileDirectory!!)
                try {

                    val partMap = parts.partMap

                    val articleType = partMap["articleType"]!![0]
                    println(articleType.fieldName) // "articleType"
                    println(articleType.headers) // {Content-Disposition=form-data; name="articleType"}
                    println(articleType.length) // 8 bytes
                    println(articleType.isInMemory) // true
                    println(articleType.string) // "obituary"

                    val simple7bit = partMap["uploadManuscript"]!![0]
                    println(simple7bit.fieldName) // "uploadManuscript"
                    println(simple7bit.fileName) // "simple7bit.txt"
                    println(simple7bit.headers) // {Content-Disposition => form-data; name="uploadManuscript"; filename="simple7bit.txt"
                    // Content-Type => text/plain}
                    println(simple7bit.length) // 8221 bytes
                    println(simple7bit.isInMemory) // false
                    simple7bit.newInputStream // stream of the contents of the file
                } finally {
                    parts.close()
                }
            }
        } catch (e: IOException) {
            // general stream exceptions
        }

    }

    @Test
    @Throws(Exception::class)
    fun uploadMultipleFilesAndFields() {
        val boundary = "-----1234"
        val multipartFormContentsStream = ByteArrayInputStream(ValidMultipartFormBuilder(boundary)
            .file("file", "foo.tab", "text/whatever", "This is the content of the file\n")
            .field("field", "fieldValue" + StreamingMultipartFormHappyTests.CR_LF + "with cr lf")
            .field("multi", "value1")
            .file("anotherFile", "BAR.tab", "text/something", "This is another file\n")
            .field("multi", "value2")
            .build())
        val form = StreamingMultipartFormParts.parse(boundary.toByteArray(UTF_8), multipartFormContentsStream, UTF_8)

        val parts = MultipartFormMap.formMap(form, UTF_8, 1024, TEMPORARY_FILE_DIRECTORY)
        val partMap = parts.partMap

        assertThat<String>(partMap["file"]!![0].fileName, equalTo("foo.tab"))
        assertThat<String>(partMap["anotherFile"]!![0].fileName, equalTo("BAR.tab"))
        StreamingMultipartFormHappyTests.compareOneStreamToAnother(partMap["field"]!![0].newInputStream, ByteArrayInputStream(("fieldValue" + StreamingMultipartFormHappyTests.CR_LF + "with cr lf").toByteArray()))
        StreamingMultipartFormHappyTests.compareOneStreamToAnother(partMap["multi"]!![0].newInputStream, ByteArrayInputStream("value1".toByteArray()))
        StreamingMultipartFormHappyTests.compareOneStreamToAnother(partMap["multi"]!![1].newInputStream, ByteArrayInputStream("value2".toByteArray()))
        parts.close()
    }

    @Test
    @Throws(Exception::class)
    fun canLoadComplexRealLifeSafariExample() {
        val form = safariExample()

        val parts = MultipartFormMap.formMap(form, UTF_8, 1024000, TEMPORARY_FILE_DIRECTORY)
        val partMap = parts.partMap
        allFieldsAreLoadedCorrectly(partMap, true, true, true, true)
        parts.close()

    }

    @Test
    @Throws(Exception::class)
    fun throwsExceptionIfFormIsTooBig() {
        val form = StreamingMultipartFormParts.parse(
            "----WebKitFormBoundary6LmirFeqsyCQRtbj".toByteArray(UTF_8),
            FileInputStream("examples/safari-example.multipart"),
            UTF_8,
            1024
        )

        try {
            MultipartFormMap.formMap(form, UTF_8, 1024, TEMPORARY_FILE_DIRECTORY)
            fail("should have failed because the form is too big")
        } catch (e: Throwable) {
            assertThat<String>(e.message, containsString("Form contents was longer than 1024 bytes"))
        }

    }

    @Test
    @Throws(Exception::class)
    fun savesAllPartsToDisk() {
        val form = safariExample()

        val parts = MultipartFormMap.formMap(form, UTF_8, 100, TEMPORARY_FILE_DIRECTORY)
        val partMap = parts.partMap

        allFieldsAreLoadedCorrectly(partMap, false, false, false, false)

        assertThat(temporaryFileList()!!.size, equalTo(4))
        parts.close()
        assertThat(temporaryFileList()!!.size, equalTo(0))
    }

    @Test
    @Throws(Exception::class)
    fun savesSomePartsToDisk() {
        val form = safariExample()

        val parts = MultipartFormMap.formMap(form, UTF_8, 1024 * 4, TEMPORARY_FILE_DIRECTORY)
        val partMap = parts.partMap

        allFieldsAreLoadedCorrectly(partMap, false, true, true, false)

        val files = temporaryFileList()
        assertPartSaved("simple7bit.txt", files)
        assertPartSaved("starbucks.jpeg", files)
        assertThat(files!!.size, equalTo(2))

        parts.close()
        assertThat(temporaryFileList()!!.size, equalTo(0))
    }

    @Test
    @Throws(Exception::class)
    fun throwsExceptionIfMultipartMalformed() {
        val form = StreamingMultipartFormParts.parse(
            "---2345".toByteArray(UTF_8),
            ByteArrayInputStream(("-----2345" + StreamingMultipartFormHappyTests.CR_LF +
                "Content-Disposition: form-data; name=\"name\"" + StreamingMultipartFormHappyTests.CR_LF +
                "" + StreamingMultipartFormHappyTests.CR_LF +
                "value" + // no CR_LF

                "-----2345--" + StreamingMultipartFormHappyTests.CR_LF).toByteArray()),
            UTF_8)

        try {
            MultipartFormMap.formMap(form, UTF_8, 1024 * 4, TEMPORARY_FILE_DIRECTORY)
            fail("Should have thrown an Exception")
        } catch (e: Throwable) {
            assertThat<String>(e.message, equalTo("Boundary must be proceeded by field separator, but didn't find it"))
        }

    }

    @Throws(IOException::class)
    private fun safariExample(): Iterable<StreamingPart> {
        return StreamingMultipartFormParts.parse(
            "----WebKitFormBoundary6LmirFeqsyCQRtbj".toByteArray(UTF_8),
            FileInputStream("examples/safari-example.multipart"),
            UTF_8
        )
    }

    @Throws(IOException::class)
    private fun allFieldsAreLoadedCorrectly(partMap: Map<String, List<Part>>, simple7bit: Boolean, file: Boolean, txt: Boolean, jpeg: Boolean) {

        assertFileIsCorrect(partMap["uploadManuscript"]!![0], "simple7bit.txt", simple7bit)
        assertFileIsCorrect(partMap["uploadManuscript"]!![2], "utf8\uD83D\uDCA9.file", file)

        val articleType = partMap["articleType"]!![0]
        assertTrue("articleType", articleType.isInMemory)
        assertThat(articleType.string, equalTo("obituary"))

        assertFileIsCorrect(partMap["uploadManuscript"]!![3], "utf8\uD83D\uDCA9.txt", txt)
        assertFileIsCorrect(partMap["uploadManuscript"]!![1], "starbucks.jpeg", jpeg)

    }

    private fun assertPartSaved(fileName: String, files: Array<String>?) {
        assertTrue(
            "couldn't find " + fileName + " in " + Arrays.toString(files),
            files!![0].contains(fileName) || files[1].contains(fileName))
    }

    @Throws(IOException::class)
    private fun assertFileIsCorrect(filePart: Part, expectedFilename: String, inMemory: Boolean) {
        assertFileIsCorrect(filePart, expectedFilename, filePart.newInputStream, inMemory)
    }

    @Throws(IOException::class)
    private fun assertFileIsCorrect(filePart: Part, expectedFilename: String, inputStream: InputStream, inMemory: Boolean) {
        assertThat(expectedFilename + " in memory?", filePart.isInMemory, equalTo(inMemory))
        assertThat<String>(filePart.fileName, equalTo(expectedFilename))
        StreamingMultipartFormHappyTests.compareStreamToFile(inputStream, filePart.fileName)
    }

    private fun temporaryFileList(): Array<String>? {
        return TEMPORARY_FILE_DIRECTORY.list()
    }

    companion object {
        val TEMPORARY_FILE_DIRECTORY = File("./out/tmp")

        init {
            TEMPORARY_FILE_DIRECTORY.mkdirs()
        }
    }
}
