package org.samc.android

import org.samc.core.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Preserves original XLSX parts and adds legacy Excel cell notes. */
object AndroidWorkbookChecker {
    private const val S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val O = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val C = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val V = "urn:schemas-microsoft-com:vml"
    private const val X = "urn:schemas-microsoft-com:office:excel"

    fun check(input: InputStream, name: String, types: Set<ActivityType>, outputDir: File): FileCheckResult {
        val parts = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                require(parts.size < 20000) { "工作簿内容过多" }
                if (entry.isDirectory) continue
                val data = ByteArrayOutputStream()
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 128 * 1024 * 1024) { "工作簿过大，请使用桌面版" }
                    data.write(buffer, 0, n)
                }
                require(entry.name !in parts) { "工作簿含重复文件" }
                parts[entry.name] = data.toByteArray()
            }
        }
        require("xl/workbook.xml" in parts) { "不是有效的 .xlsx 文件，或文件已加密" }
        val first = elements(xml(parts.getValue("xl/workbook.xml")), S, "sheet").first()
        val sheetRel = elements(xml(parts.getValue("xl/_rels/workbook.xml.rels")), R, "Relationship")
            .first { it.getAttribute("Id") == first.getAttributeNS(O, "id") }
        val path = resolve("xl/workbook.xml", sheetRel.getAttribute("Target"))
        val sheet = xml(parts.getValue(path))
        val strings = parts["xl/sharedStrings.xml"]?.let { elements(xml(it), S, "si").map { e -> text(e, "t") } } ?: emptyList()
        val grid = CellGrid(elements(sheet, S, "c").associate { cell ->
            val raw = text(cell, "v")
            cell.getAttribute("r") to when (cell.getAttribute("t")) {
                "s" -> strings.getOrNull(raw.toIntOrNull() ?: -1)
                "inlineStr" -> text(cell, "t")
                "b" -> if (raw == "1") "TRUE" else "FALSE"
                else -> raw
            }
        })
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val title = grid.value("E1").orEmpty()
        val type = when {
            "非常规" in title -> ActivityType.UNCONVENTIONAL
            "常规" in title -> ActivityType.CONVENTIONAL
            else -> return FileCheckResult(name, ActivityType.CONVENTIONAL, false,
                listOf(CheckFailure("类型识别", "E1 未识别为常规或非常规活动表")), time)
        }
        if (type !in types) return FileCheckResult(name, type, false,
            listOf(CheckFailure("检查范围", "此活动类型未勾选，请确认检查范围")), time)
        val result = SamcRules.checkFile(name, grid, type).copy(checkedAt = time)
        if (result.passed) return result
        annotate(parts, sheet, path, result.failures)
        outputDir.mkdirs()
        val file = File.createTempFile("SC_${File(name).nameWithoutExtension.take(80)}_批注版_", ".xlsx", outputDir)
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                parts.forEach { (key, value) ->
                    zip.putNextEntry(ZipEntry(key)); zip.write(value); zip.closeEntry()
                }
            }
        } catch (e: Exception) { file.delete(); throw e }
        return result.copy(annotatedFilePath = file.absolutePath)
    }

    private fun annotate(parts: MutableMap<String, ByteArray>, sheet: Document, path: String, failures: List<CheckFailure>) {
        val parent = path.substringBeforeLast('/')
        val relPath = "$parent/_rels/${path.substringAfterLast('/')}.rels"
        val rels = parts[relPath]?.let(::xml) ?: document(R, "Relationships")
        fun relation(kind: String) = elements(rels, R, "Relationship").firstOrNull { it.getAttribute("Type") == "$O/$kind" }
        fun addRelation(kind: String, target: String): String {
            var i = 1
            val ids = elements(rels, R, "Relationship").map { it.getAttribute("Id") }.toSet()
            while ("sc$i" in ids) i++
            val id = "sc$i"
            rels.documentElement.appendChild(rels.createElementNS(R, "Relationship").apply {
                setAttribute("Id", id); setAttribute("Type", "$O/$kind"); setAttribute("Target", target)
            })
            return id
        }
        fun unique(base: String, ext: String): String {
            var i = 1
            while ("$parent/$base$i.$ext" in parts) i++
            return "$parent/$base$i.$ext"
        }
        val cr = relation("comments")
        val cp = cr?.let { resolve(path, it.getAttribute("Target")) }
            ?: unique("scComments", "xml").also { addRelation("comments", it.substringAfterLast('/')) }
        val comments = parts[cp]?.let(::xml) ?: document(S, "comments").apply {
            documentElement.appendChild(createElementNS(S, "authors"))
            documentElement.appendChild(createElementNS(S, "commentList"))
        }
        val authorId = elements(comments, S, "author").size
        comments.getElementsByTagNameNS(S, "authors").item(0).appendChild(
            comments.createElementNS(S, "author").apply { textContent = "Smart Checker" })
        val vr = relation("vmlDrawing")
        val vp = vr?.let { resolve(path, it.getAttribute("Target")) } ?: unique("scNotes", "vml")
        val vid = vr?.getAttribute("Id") ?: addRelation("vmlDrawing", vp.substringAfterLast('/'))
        val vml = parts[vp]?.let(::xml) ?: document(null, "xml")
        if (elements(vml, V, "shapetype").isEmpty()) {
            vml.documentElement.appendChild(vml.createElementNS(V, "v:shapetype").apply {
                setAttribute("id", "_x0000_t202"); setAttribute("coordsize", "21600,21600")
                setAttributeNS("urn:schemas-microsoft-com:office:office", "o:spt", "202")
                setAttribute("path", "m,l,21600r21600,l21600,xe")
            })
        }
        val notes = linkedMapOf<String, MutableList<String>>()
        failures.forEach { f -> f.cells.forEach { notes.getOrPut(it) { mutableListOf() }.add(f.displayText) } }
        val ids = elements(vml, V, "shape").map { it.getAttribute("id") }.toSet()
        var index = 1025
        notes.forEach { (ref, messages) ->
            val old = elements(comments, S, "comment").firstOrNull { it.getAttribute("ref") == ref }
            val prior = old?.let { text(it, "t") }.orEmpty()
            old?.parentNode?.removeChild(old)
            val comment = comments.createElementNS(S, "comment").apply {
                setAttribute("ref", ref); setAttribute("authorId", authorId.toString())
            }
            comment.appendChild(comments.createElementNS(S, "text").apply {
                appendChild(comments.createElementNS(S, "t").apply {
                    textContent = listOf(prior, "Smart Checker\n${messages.joinToString("\n")}").filter { it.isNotEmpty() }.joinToString("\n\n")
                })
            })
            comments.getElementsByTagNameNS(S, "commentList").item(0).appendChild(comment)
            if (old == null) {
                while ("_x0000_s$index" in ids) index++
                val shape = vml.createElementNS(V, "v:shape").apply {
                    setAttribute("id", "_x0000_s${index++}"); setAttribute("type", "#_x0000_t202")
                    setAttribute("style", "position:absolute;width:300pt;height:100pt;visibility:hidden")
                    setAttribute("fillcolor", "#ffffe1")
                }
                shape.appendChild(vml.createElementNS(V, "v:textbox").apply { appendChild(vml.createElement("div")) })
                val client = vml.createElementNS(X, "x:ClientData").apply { setAttribute("ObjectType", "Note") }
                var col = 0
                ref.takeWhile { it.isLetter() }.forEach { col = col * 26 + (it.uppercaseChar() - 'A' + 1) }
                client.appendChild(vml.createElementNS(X, "x:Row").apply { textContent = (ref.dropWhile { it.isLetter() }.toInt() - 1).toString() })
                client.appendChild(vml.createElementNS(X, "x:Column").apply { textContent = (col - 1).toString() })
                shape.appendChild(client); vml.documentElement.appendChild(shape)
            }
        }
        val legacy = elements(sheet, S, "legacyDrawing").firstOrNull() ?: sheet.createElementNS(S, "legacyDrawing").also {
            val trailing = setOf("legacyDrawingHF", "picture", "oleObjects", "controls", "webPublishItems", "tableParts", "extLst")
            val nodes = sheet.documentElement.childNodes
            sheet.documentElement.insertBefore(it, (0 until nodes.length).map { n -> nodes.item(n) }.firstOrNull { n -> n.localName in trailing })
        }
        legacy.setAttributeNS(O, "r:id", vid)
        val ct = xml(parts.getValue("[Content_Types].xml"))
        if (elements(ct, C, "Override").none { it.getAttribute("PartName") == "/$cp" }) {
            ct.documentElement.appendChild(ct.createElementNS(C, "Override").apply {
                setAttribute("PartName", "/$cp"); setAttribute("ContentType", "application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml")
            })
        }
        if (elements(ct, C, "Default").none { it.getAttribute("Extension") == "vml" }) {
            ct.documentElement.appendChild(ct.createElementNS(C, "Default").apply {
                setAttribute("Extension", "vml"); setAttribute("ContentType", "application/vnd.openxmlformats-officedocument.vmlDrawing")
            })
        }
        parts[path] = bytes(sheet); parts[relPath] = bytes(rels); parts[cp] = bytes(comments)
        parts[vp] = bytes(vml); parts["[Content_Types].xml"] = bytes(ct)
    }

    private fun factory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }
    private fun xml(data: ByteArray): Document {
        require(!data.toString(Charsets.UTF_8).replace("\u0000", "").contains("<!DOCTYPE", true)) { "不支持含实体声明的工作簿" }
        return factory().newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities are disabled") }
        }.parse(ByteArrayInputStream(data))
    }
    private fun document(ns: String?, root: String) = factory().newDocumentBuilder().newDocument().apply {
        appendChild(createElementNS(ns, root))
    }
    private fun elements(doc: Document, ns: String, tag: String): List<Element> =
        doc.getElementsByTagNameNS(ns, tag).let { n -> (0 until n.length).map { n.item(it) as Element } }
    private fun text(element: Element, tag: String) =
        element.getElementsByTagNameNS(S, tag).let { n -> (0 until n.length).joinToString("") { n.item(it).textContent } }
    private fun bytes(doc: Document) = ByteArrayOutputStream().also {
        TransformerFactory.newInstance().newTransformer().apply { setOutputProperty(OutputKeys.ENCODING, "UTF-8") }
            .transform(DOMSource(doc), StreamResult(it))
    }.toByteArray()
    private fun resolve(source: String, target: String): String {
        val path = if (target.startsWith('/')) target.drop(1) else "${source.substringBeforeLast('/')}/$target"
        val segments = mutableListOf<String>()
        path.split('/').forEach { when (it) {
            ".." -> { require(segments.isNotEmpty()); segments.removeAt(segments.lastIndex) }
            ".", "" -> Unit
            else -> segments.add(it)
        } }
        return segments.joinToString("/")
    }
}
