package com.belgetarayici.share

import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Body of a `.btz`: scanned pages, or — for PDFs — the original file in `original.pdf` (no pages). */
data class SharedDocument(val name: String, val pages: List<Page>, val pdf: ByteArray? = null) {
    data class Page(val jpeg: ByteArray, val text: String, val words: String)

    fun writeTo(out: OutputStream) = write(out, name, pages.size, pdf) { pages[it] }

    companion object {
        /** Streams a document without holding every page in memory; [page] is called in order. */
        fun write(out: OutputStream, name: String, pageCount: Int, pdf: ByteArray? = null, page: (Int) -> Page) {
            val zip = ZipOutputStream(out)
            zip.setLevel(0) // JPEG pages are already compressed
            val manifest = Properties().apply {
                setProperty("name", name)
                setProperty("pages", pageCount.toString())
            }
            zip.putNextEntry(ZipEntry(MANIFEST))
            OutputStreamWriter(zip, Charsets.UTF_8).let { manifest.store(it, null); it.flush() }
            zip.closeEntry()
            pdf?.let { zip.entry(PDF, it) }
            for (i in 0 until pageCount) {
                val p = page(i)
                zip.entry("pages/$i.jpg", p.jpeg)
                zip.entry("pages/$i.txt", p.text.toByteArray(Charsets.UTF_8))
                zip.entry("pages/$i.words", p.words.toByteArray(Charsets.UTF_8))
            }
            zip.finish()
        }

        private const val MANIFEST = "manifest.properties"
        private const val PDF = "original.pdf"
        private const val MAX_PAGES = 2000
        private const val MAX_ENTRY = 64L * 1024 * 1024
        private const val MAX_PDF = 160L * 1024 * 1024

        fun readFrom(input: InputStream): SharedDocument {
            val zip = ZipInputStream(input)
            val entries = HashMap<String, ByteArray>()
            var manifest: Properties? = null
            var pdf: ByteArray? = null
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                if (e.name == MANIFEST) {
                    manifest = Properties().apply { load(InputStreamReader(zip, Charsets.UTF_8)) }
                } else if (e.name == PDF) {
                    pdf = zip.readBounded(MAX_PDF)
                } else if (e.name.startsWith("pages/")) {
                    entries[e.name] = zip.readBounded(MAX_ENTRY)
                }
                if (entries.size > MAX_PAGES * 3) error("Too many entries")
            }
            val m = manifest ?: error("Missing manifest")
            val count = m.getProperty("pages")?.toIntOrNull()?.takeIf { it in 0..MAX_PAGES } ?: error("Bad manifest")
            val pages = (0 until count).map { i ->
                Page(
                    jpeg = entries["pages/$i.jpg"] ?: error("Missing page $i"),
                    text = entries["pages/$i.txt"]?.toString(Charsets.UTF_8).orEmpty(),
                    words = entries["pages/$i.words"]?.toString(Charsets.UTF_8).orEmpty(),
                )
            }
            return SharedDocument(m.getProperty("name").orEmpty(), pages, pdf)
        }

        private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
            putNextEntry(ZipEntry(name))
            write(bytes)
            closeEntry()
        }

        private fun InputStream.readBounded(max: Long): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = read(buf)
                if (n < 0) break
                total += n
                if (total > max) error("Entry too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
