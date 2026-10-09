package app.drift.source.sleepasandroid

import java.io.Reader

/**
 * Minimal RFC 4180 reader: quoted fields, doubled quotes, newlines inside quotes, CRLF or LF, leading BOM.
 * The delimiter is detected from the first line (comma, semicolon or tab).
 */
internal class CsvReader(private val reader: Reader) {
    private var delimiter: Char? = null
    private var peeked = -2
    private var first = true

    private fun read(): Int {
        if (peeked != -2) return peeked.also { peeked = -2 }
        return reader.read()
    }

    private fun peek(): Int {
        if (peeked == -2) peeked = reader.read()
        return peeked
    }

    /** Returns the next row, or null at end of input. A fully blank line yields an empty list. */
    fun nextRow(): List<String>? {
        var c = read()
        if (first) {
            first = false
            if (c == 0xFEFF) c = read()
        }
        if (c == -1) return null
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var wasQuoted = false
        while (true) {
            if (c == -1) break
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    if (peek() == '"'.code) { read(); cell.append('"') } else inQuotes = false
                } else {
                    cell.append(ch)
                }
            } else when {
                ch == '"' && cell.isEmpty() && !wasQuoted -> { inQuotes = true; wasQuoted = true }
                ch == '\r' -> { if (peek() == '\n'.code) read(); break }
                ch == '\n' -> break
                isDelimiter(ch) -> { cells += cell.toString(); cell.setLength(0); wasQuoted = false }
                else -> cell.append(ch)
            }
            c = read()
        }
        cells += cell.toString()
        if (cells.size == 1 && cells[0].isEmpty() && !wasQuoted) return emptyList()
        return cells
    }

    private fun isDelimiter(ch: Char): Boolean {
        val d = delimiter
        if (d != null) return ch == d
        if (ch == ',' || ch == ';' || ch == '\t') {
            delimiter = ch
            return true
        }
        return false
    }
}
