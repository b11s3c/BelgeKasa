package com.belgetarayici.io

import com.belgetarayici.i18n.tr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Minimal OOXML (.docx) writer: one section per scanned page (image, then its text). */
object DocxWriter {
    class Page(val jpeg: ByteArray?, val widthPx: Int, val heightPx: Int, val text: String)

    private const val EMU_PER_TWIP = 635L
    private const val PAGE_W = 11906L // A4, twips
    private const val PAGE_H = 16838L
    private const val MARGIN = 720L

    fun write(out: OutputStream, pages: List<Page>) {
        val zip = ZipOutputStream(out)
        val body = StringBuilder()
        val rels = StringBuilder()
        val maxW = (PAGE_W - 2 * MARGIN) * EMU_PER_TWIP
        val maxH = (PAGE_H - 2 * MARGIN - 400) * EMU_PER_TWIP

        pages.forEachIndexed { i, page ->
            if (i > 0) body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            if (page.jpeg != null && page.widthPx > 0 && page.heightPx > 0) {
                val n = i + 1
                val rid = "rIdImg$n"
                zip.entry("word/media/page$n.jpeg", page.jpeg)
                rels.append("""<Relationship Id="$rid" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/page$n.jpeg"/>""")
                val scale = minOf(maxW.toDouble() / page.widthPx, maxH.toDouble() / page.heightPx)
                val cx = (page.widthPx * scale).toLong()
                val cy = (page.heightPx * scale).toLong()
                body.append(
                    """<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">""" +
                        """<wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$n" name="Sayfa $n"/>""" +
                        """<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
                        """<pic:pic><pic:nvPicPr><pic:cNvPr id="$n" name="page$n.jpeg"/><pic:cNvPicPr/></pic:nvPicPr>""" +
                        """<pic:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""" +
                        """<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""" +
                        """</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""",
                )
                if (page.text.isNotBlank()) body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }
            page.text.trimEnd().lines().forEach { line ->
                body.append("""<w:p><w:r><w:t xml:space="preserve">""").append(escape(line)).append("</w:t></w:r></w:p>")
            }
        }

        zip.entry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                """<Default Extension="xml" ContentType="application/xml"/>""" +
                """<Default Extension="jpeg" ContentType="image/jpeg"/>""" +
                """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
                """</Types>""",
        )
        zip.entry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
                """</Relationships>""",
        )
        zip.entry(
            "word/_rels/document.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""",
        )
        zip.entry(
            "word/document.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
                """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
                """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
                """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
                """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
                """<w:body>$body<w:sectPr><w:pgSz w:w="$PAGE_W" w:h="$PAGE_H"/>""" +
                """<w:pgMar w:top="$MARGIN" w:right="$MARGIN" w:bottom="$MARGIN" w:left="$MARGIN" w:header="0" w:footer="0" w:gutter="0"/>""" +
                """</w:sectPr></w:body></w:document>""",
        )
        zip.finish()
    }

    private fun ZipOutputStream.entry(name: String, text: String) = entry(name, text.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    internal fun escape(s: String): String = buildString(s.length) {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            c == '\t' || c.code >= 0x20 && c !in '￾'..'￿' -> append(c)
            else -> Unit // XML 1.0 forbids other control characters
        }
    }
}

/** Reads the body of a .docx into ordered text paragraphs and embedded images. */
object DocxReader {
    sealed interface Block {
        data class Text(val text: String, val heading: Boolean) : Block
        class Image(val bytes: ByteArray) : Block
    }

    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val MAX_PART = 64L * 1024 * 1024

    fun read(input: InputStream): List<Block> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.removePrefix("/")
                if (name == "word/document.xml" || name == "word/_rels/document.xml.rels" || name.startsWith("word/media/")) {
                    parts[name] = zip.readBounded()
                }
            }
        }
        val document = parts["word/document.xml"] ?: error(tr("Geçerli bir DOCX değil"))
        val targets = parts["word/_rels/document.xml.rels"]?.let(::parseRelationships).orEmpty()
        val doc = parse(document)
        val body = doc.getElementsByTagNameNS(W, "body").item(0) as? Element ?: return emptyList()
        val blocks = ArrayList<Block>()
        for (child in body.childElements()) {
            when (child.localName) {
                "p" -> paragraph(child, targets, parts, blocks)
                "tbl" -> child.descendants(W, "tr").forEach { row ->
                    val cells = row.childElements().filter { it.localName == "tc" }.map { textOf(it).trim() }
                    if (cells.any { it.isNotEmpty() }) blocks += Block.Text(cells.joinToString("  |  "), false)
                }
            }
        }
        return blocks
    }

    private fun paragraph(p: Element, targets: Map<String, String>, parts: Map<String, ByteArray>, out: MutableList<Block>) {
        val style = p.descendants(W, "pStyle").firstOrNull()?.getAttributeNS(W, "val").orEmpty()
        val text = textOf(p)
        p.descendants(A, "blip").forEach { blip ->
            val target = targets[blip.getAttributeNS(R, "embed")] ?: return@forEach
            val path = "word/" + target.removePrefix("/word/").removePrefix("./")
            parts[path]?.let { out += Block.Image(it) }
        }
        out += Block.Text(text, style.startsWith("Heading", ignoreCase = true) || style.startsWith("Title", ignoreCase = true))
    }

    private fun textOf(root: Element): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            if (n is Element && n.namespaceURI == W) when (n.localName) {
                "t" -> { sb.append(n.textContent); return }
                "tab" -> { sb.append('\t'); return }
                "br", "cr" -> { sb.append('\n'); return }
                "p" -> if (n !== root && sb.isNotEmpty()) sb.append('\n')
            }
            var c = n.firstChild
            while (c != null) { walk(c); c = c.nextSibling }
        }
        walk(root)
        return sb.toString()
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val doc = parse(bytes)
        val list = doc.getElementsByTagNameNS("*", "Relationship")
        return (0 until list.length).associate {
            val e = list.item(it) as Element
            e.getAttribute("Id") to e.getAttribute("Target")
        }
    }

    private fun parse(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }

    private fun Element.childElements(): List<Element> {
        val out = ArrayList<Element>()
        var c = firstChild
        while (c != null) { if (c is Element) out += c; c = c.nextSibling }
        return out
    }

    private fun Element.descendants(ns: String, local: String): List<Element> {
        val list = getElementsByTagNameNS(ns, local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun InputStream.readBounded(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_PART) error(tr("DOCX bölümü çok büyük"))
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
