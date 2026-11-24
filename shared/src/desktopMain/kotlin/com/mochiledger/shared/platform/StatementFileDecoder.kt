package com.mochiledger.shared.platform

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Decodes WeChat official `.xlsx` (OOXML zip) and Alipay official `.csv` (UTF-8 / GB18030)
 * statement files into normalized CSV text for [com.mochiledger.shared.parser.BillStatementImporter].
 */
object StatementFileDecoder {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun decode(fileName: String, bytes: ByteArray): String {
        val isZipMagic = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte()
        if (isZipMagic || fileName.endsWith(".xlsx", ignoreCase = true)) {
            return parseXlsxToCsv(bytes)
        }
        return decodeCsvBytes(bytes)
    }

    private fun decodeCsvBytes(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        val looksLikeUtf8 = !utf8.contains('\uFFFD') &&
            (utf8.contains("交易时间") || utf8.contains("交易对方") || utf8.contains("微信") || utf8.contains("支付宝"))
        if (looksLikeUtf8) return utf8.removePrefix("\uFEFF")
        return runCatching {
            String(bytes, Charset.forName("GB18030")).removePrefix("\uFEFF")
        }.getOrElse { utf8.removePrefix("\uFEFF") }
    }

    private fun parseXlsxToCsv(bytes: ByteArray): String {
        var sharedStringsXml: ByteArray? = null
        var sheetXml: ByteArray? = null
        var fallbackSheetXml: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.lowercase()
                when {
                    name == "xl/sharedstrings.xml" -> sharedStringsXml = zis.readBytes()
                    name == "xl/worksheets/sheet1.xml" -> sheetXml = zis.readBytes()
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") && fallbackSheetXml == null -> {
                        fallbackSheetXml = zis.readBytes()
                    }
                }
                zis.closeEntry()
            }
        }

        val targetSheet = sheetXml ?: fallbackSheetXml
            ?: throw IllegalArgumentException("无法在 XLSX 账单中找到工作表数据")

        val sharedStrings = sharedStringsXml?.let { parseSharedStrings(it) } ?: emptyList()
        val rows = parseWorksheetRows(targetSheet, sharedStrings)

        return rows.joinToString("\n") { row ->
            row.joinToString(",") { cell -> escapeCsvCell(cell) }
        }
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val doc = newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val siNodes = doc.getElementsByTagName("si")
        val result = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as? Element ?: continue
            val tNodes = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) {
                sb.append(tNodes.item(j).textContent.orEmpty())
            }
            result.add(sb.toString())
        }
        return result
    }

    private fun parseWorksheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val rowNodes = doc.getElementsByTagName("row")
        val rows = ArrayList<List<String>>(rowNodes.length)

        for (r in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(r) as? Element ?: continue
            val cellsByCol = mutableMapOf<Int, String>()
            var maxCol = -1
            var sequentialCol = 0

            val children = rowEl.childNodes
            for (c in 0 until children.length) {
                val node = children.item(c)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val cellEl = node as Element
                if (cellEl.localName != "c" && cellEl.tagName != "c") continue

                val ref = cellEl.getAttribute("r")
                val colIdx = if (ref.isNotBlank()) columnIndexFromRef(ref) else sequentialCol
                sequentialCol = colIdx + 1
                if (colIdx > maxCol) maxCol = colIdx

                val value = extractCellValue(cellEl, sharedStrings, colIdx)
                cellsByCol[colIdx] = value
            }

            if (maxCol >= 0) {
                val line = List(maxCol + 1) { idx -> cellsByCol[idx].orEmpty() }
                rows.add(line)
            }
        }
        return rows
    }

    private fun extractCellValue(cellEl: Element, sharedStrings: List<String>, colIdx: Int): String {
        val type = cellEl.getAttribute("t")
        if (type == "inlineStr") {
            val tNodes = cellEl.getElementsByTagName("t")
            val sb = StringBuilder()
            for (i in 0 until tNodes.length) {
                sb.append(tNodes.item(i).textContent.orEmpty())
            }
            return sb.toString().trim()
        }

        val vNodes = cellEl.getElementsByTagName("v")
        val raw = if (vNodes.length > 0) vNodes.item(0).textContent.orEmpty().trim() else ""
        if (raw.isEmpty()) return ""

        return when (type) {
            "s" -> {
                val idx = raw.toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx].trim() else raw
            }
            "str", "b", "e" -> raw
            else -> {
                // Convert Excel serial date in column 0 (e.g. WeChat transaction timestamp 46350.5105)
                val num = raw.toDoubleOrNull()
                if (colIdx == 0 && num != null && num in 40000.0..65000.0) {
                    formatExcelSerialDate(num)
                } else {
                    raw
                }
            }
        }
    }

    private fun formatExcelSerialDate(serial: Double): String {
        val days = floor(serial).toLong()
        val fracSeconds = ((serial - days) * 86400.0).roundToLong().coerceIn(0L, 86399L)
        val date = LocalDate.of(1899, 12, 30).plusDays(days)
        val dt = LocalDateTime.of(
            date.year,
            date.monthValue,
            date.dayOfMonth,
            (fracSeconds / 3600).toInt(),
            ((fracSeconds % 3600) / 60).toInt(),
            (fracSeconds % 60).toInt(),
        )
        return dt.format(dateTimeFormatter)
    }

    private fun columnIndexFromRef(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun escapeCsvCell(value: String): String {
        val cleaned = value.replace("\r\n", " ").replace('\n', ' ')
        return if (cleaned.contains(',') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }

    private fun newDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder()
}
