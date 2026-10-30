package com.dvnnfrr.saku.excel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.net.URLClassLoader

/**
 * Does the Excel stack actually run on Android? `assembleDebug` cannot answer that, and neither
 * can an ordinary unit test.
 *
 * ## Why a classloader rather than a device
 *
 * This machine has no KVM (`/dev/kvm` absent), so the x86_64 emulator refuses to start, and no
 * physical device is attached. Even with one, a Robolectric run would not settle the question:
 * Robolectric executes on the desktop JVM, whose `java.xml` module supplies the StAX API that
 * Android lacks, so it would go green whether or not the app works on a phone.
 *
 * So the one difference that matters is reproduced directly. Android has no
 * `javax.xml.stream`, `javax.xml.stream.events` or `javax.xml.stream.util` — verified against
 * `android.jar` for API 35, which declares zero classes across all three while providing every
 * other `javax.xml.*` package the chain touches. [AndroidLikeClassLoader] takes the ordinary
 * test classpath, removes exactly those three packages, and loads the Excel libraries
 * child-first so their own references resolve through it rather than quietly picking StAX up
 * from the JDK.
 *
 * What this establishes: the reader is dead on stock Android, the bundled StAX shim revives it,
 * and the writer never needed it. What it does not establish: ART-specific verifier behaviour,
 * or anything about real-device performance. Both still want a device run before track F ships.
 */
class XlsxAndroidRuntimeTest {

    /** If this ever fails, the failure below proves nothing — it would just be a broken classpath. */
    @Test
    fun `the desktop JVM supplies StAX, which is why ordinary unit tests cannot answer this`() {
        assertNotNull(Class.forName("javax.xml.stream.XMLInputFactory"))
    }

    /**
     * Stock Android, no shim. Reproduces the crash the spike was run to find.
     */
    @Test
    fun `without the StAX shim the reader dies exactly as it would on a phone`() {
        val failure = androidLikeLoader(withStaxShim = false).use { loader ->
            runCatching {
                loader.loadClass("org.dhatim.fastexcel.reader.ReadableWorkbook")
                    .getConstructor(File::class.java)
                    .newInstance(Fixture.file)
            }.exceptionOrNull()
        }

        assertNotNull(
            "fastexcel-reader opened the workbook with javax.xml.stream removed. If this ever " +
                "happens the StAX dependency is gone and the shim can be dropped.",
            failure,
        )

        val chain = failure!!.causeChain()
        assertTrue(
            "expected the failure to name javax.xml.stream, got:\n$chain",
            chain.contains("javax.xml.stream"),
        )
        assertTrue(
            "expected a NoClassDefFoundError, got:\n$chain",
            chain.contains("NoClassDefFoundError"),
        )
    }

    /**
     * The same Android-like classpath, plus the `geronimo-stax-api_1.2_spec` jar the app now
     * ships. This is the check that says import can go on device.
     *
     * Guards the dependency too: drop `libs.stax.api` from `app/build.gradle.kts` and this
     * fails rather than shipping a build that only crashes once installed.
     */
    @Test
    fun `with the bundled StAX shim the reader parses every row on an Android classpath`() {
        androidLikeLoader(withStaxShim = true).use { loader ->
            val readableWorkbook = loader.loadClass("org.dhatim.fastexcel.reader.ReadableWorkbook")
            val workbook = readableWorkbook
                .getConstructor(File::class.java)
                .newInstance(Fixture.file)

            val sheet = readableWorkbook.getMethod("getFirstSheet").invoke(workbook)
            val rows = sheet.javaClass.getMethod("read").invoke(sheet) as List<*>
            readableWorkbook.getMethod("close").invoke(workbook)

            assertEquals("390 data rows plus the header", 391, rows.size)
        }
    }

    /**
     * The other half of the finding: export never depended on any of this.
     */
    @Test
    fun `the writer works with no StAX present at all`() {
        androidLikeLoader(withStaxShim = false).use { loader ->
            val bytes = ByteArrayOutputStream()

            val workbookClass = loader.loadClass("org.dhatim.fastexcel.Workbook")
            val workbook = workbookClass
                .getConstructor(OutputStream::class.java, String::class.java, String::class.java)
                .newInstance(bytes, "Saku", "1.0")

            val worksheet = workbookClass
                .getMethod("newWorksheet", String::class.java)
                .invoke(workbook, "Saku")

            val setValue = worksheet.javaClass.getMethod(
                "value",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            setValue.invoke(worksheet, 0, 0, "Category")
            setValue.invoke(worksheet, 1, 0, "Makan & Minum")

            workbookClass.getMethod("finish").invoke(workbook)

            val written = bytes.toByteArray()
            assertTrue("expected a non-empty workbook", written.size > 512)
            // Every .xlsx is a zip, so it opens with the local file header magic "PK".
            assertEquals(0x50, written[0].toInt() and 0xFF)
            assertEquals(0x4B, written[1].toInt() and 0xFF)
        }
    }

    /**
     * A backup is several sheets in one workbook, and restore finds them by name. That path
     * goes through `ReadableWorkbook.getSheets()` rather than `getFirstSheet()`, so it is
     * proved separately: Saku's own [XlsxReader.readAll] is loaded inside the Android-like
     * classpath, where its fastexcel references can only resolve StAX through the shim.
     */
    @Test
    fun `with the bundled StAX shim a multi-sheet workbook reads back sheet by sheet`() {
        val bytes = ByteArrayOutputStream().also { output ->
            XlsxWriter.write(
                listOf(
                    SheetTable("Saku", listOf("Note"), listOf(SheetRow(1, listOf(CellValue.Text("nasi padang"))))),
                    SheetTable("Accounts", listOf("Name"), listOf(SheetRow(1, listOf(CellValue.Text("BCA"))))),
                    SheetTable("Backup", listOf("Field", "Value"), emptyList()),
                ),
                output,
            )
        }.toByteArray()

        androidLikeLoader(withStaxShim = true).use { loader ->
            val reader = loader.loadClass("com.dvnnfrr.saku.excel.XlsxReader")
            assertTrue("XlsxReader must be defined inside the Android-like loader", reader.classLoader === loader)

            val tables = reader.getMethod("readAll", InputStream::class.java)
                .invoke(reader.getField("INSTANCE").get(null), ByteArrayInputStream(bytes)) as List<*>

            fun Any.call(getter: String): Any? = javaClass.getMethod(getter).invoke(this)
            assertEquals(listOf("Saku", "Accounts", "Backup"), tables.map { it!!.call("getSheetName") })
            assertEquals(1, (tables[1]!!.call("getRows") as List<*>).size)
        }
    }

    private fun Throwable.causeChain(): String =
        generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }
            .joinToString("\n") { "${it.javaClass.name}: ${it.message}" }

    private fun classpath(): List<File> =
        System.getProperty("java.class.path").orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map(::File)

    private fun androidLikeLoader(withStaxShim: Boolean): AndroidLikeClassLoader {
        val entries = classpath()
        val shimJar = entries.firstOrNull { it.name.contains("geronimo-stax-api") }
        check(!withStaxShim || shimJar != null) {
            "geronimo-stax-api is not on the test classpath. It is the dependency that makes " +
                "fastexcel-reader work on Android; see excel/XlsxReader.kt."
        }

        val staxSource = shimJar
            ?.takeIf { withStaxShim }
            ?.let { URLClassLoader(arrayOf(it.toURI().toURL()), null) }

        return AndroidLikeClassLoader(
            urls = entries.map { it.toURI().toURL() }.toTypedArray(),
            staxSource = staxSource,
        )
    }

    /**
     * Behaves the way Android's runtime does for the one question at hand: the three StAX
     * packages are not on the platform.
     *
     * When [staxSource] is null they cannot be loaded at all — stock Android. When it is set,
     * they resolve *only* from the shim jar, never from the JDK — Android plus the dependency
     * the app declares.
     *
     * The Excel libraries load child-first so their references to `javax.xml.stream` resolve
     * through this loader. Under normal parent-first delegation the application loader would
     * define them and hand them the JDK's StAX, which is precisely the false pass this test
     * exists to rule out.
     */
    private class AndroidLikeClassLoader(urls: Array<URL>, private val staxSource: ClassLoader?) :
        URLClassLoader(urls, AndroidLikeClassLoader::class.java.classLoader) {

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (ABSENT_ON_ANDROID.any { name == it || name.startsWith("$it.") }) {
                val fromShim = staxSource?.loadClass(name)
                    ?: throw ClassNotFoundException(
                        "$name is absent from the Android runtime — android.jar (API 35) " +
                            "declares no class in javax.xml.stream, javax.xml.stream.events " +
                            "or javax.xml.stream.util.",
                    )
                if (resolve) resolveClass(fromShim)
                return fromShim
            }

            synchronized(this) {
                findLoadedClass(name)?.let {
                    if (resolve) resolveClass(it)
                    return it
                }

                val loaded = if (CHILD_FIRST.any { prefix -> name.startsWith(prefix) }) {
                    runCatching { findClass(name) }.getOrElse { super.loadClass(name, false) }
                } else {
                    super.loadClass(name, false)
                }

                if (resolve) resolveClass(loaded)
                return loaded
            }
        }

        private companion object {
            val ABSENT_ON_ANDROID = listOf(
                "javax.xml.stream",
                "javax.xml.stream.events",
                "javax.xml.stream.util",
            )

            /**
             * The Excel libraries, the StAX implementation they instantiate directly, and
             * Saku's own Excel wrappers, so a test can drive those on the same classpath.
             */
            val CHILD_FIRST = listOf(
                "com.dvnnfrr.saku.excel.",
                "org.dhatim.",
                "com.fasterxml.aalto.",
                "org.codehaus.stax2.",
                "com.github.rzymek.",
                "org.apache.commons.compress.",
            )
        }
    }
}
