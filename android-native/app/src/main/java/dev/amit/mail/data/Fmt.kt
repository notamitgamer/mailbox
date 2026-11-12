package dev.amit.mail.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

data class Addr(val name: String, val addr: String)

private val ADDR_RE = Regex("^\\s*\"?([^\"<]*?)\"?\\s*<([^>]+)>\\s*$")

fun parseAddr(s: String?): Addr {
    val m = ADDR_RE.matchEntire(s ?: "")
    return if (m != null) Addr(m.groupValues[1].trim(), m.groupValues[2].trim()) else Addr("", (s ?: "").trim())
}

// Automated senders (no-reply@render.com) have no display name: show the service ("Render") instead of the raw address.
private val PERSONAL_HOST = Regex("^(gmail|googlemail|yahoo|ymail|outlook|hotmail|live|msn|icloud|me|proton|protonmail|pm|aol|zoho|gmx|yandex|rediffmail)\\.")
private val AUTO_LOCAL = Regex(
    "^(no-?reply|do-?not-?reply|donotreply|notifications?|notify|support|help|hello|hi|info|team|news|newsletter|mail|mailer|alerts?|updates?|billing|accounts?|admin|events?|contact|service|security|digest|marketing|offers|careers|jobs|receipts?|orders?)([._+-].*)?$",
    RegexOption.IGNORE_CASE,
)
private val BRAND_CASE = mapOf(
    "github" to "GitHub", "hackerrank" to "HackerRank", "linkedin" to "LinkedIn", "youtube" to "YouTube",
    "openai" to "OpenAI", "paypal" to "PayPal", "stackoverflow" to "Stack Overflow", "gitlab" to "GitLab",
    "cloudflare" to "Cloudflare", "npmjs" to "npm",
)

private fun serviceOf(addr: String): String {
    val at = addr.lastIndexOf('@')
    if (at < 1) return ""
    val host = addr.substring(at + 1).lowercase()
    if (PERSONAL_HOST.containsMatchIn(host)) return ""
    val parts = host.split('.')
    if (parts.size < 2) return ""
    val second = parts[parts.size - 2]
    return if (parts.size > 2 && second.length <= 3 && parts.last().length == 2) parts[parts.size - 3] else second
}

private fun brandOf(addr: String): String {
    val at = addr.lastIndexOf('@')
    val local = if (at >= 0) addr.substring(0, at) else addr
    val name = if (AUTO_LOCAL.matches(local)) serviceOf(addr) else ""
    return if (name.isEmpty()) "" else BRAND_CASE[name] ?: name.replaceFirstChar { it.uppercase() }
}

/** The name shown for a sender: display name, else the service for automated mail, else the address. */
fun label(s: String?): String {
    val p = parseAddr(s)
    return p.name.ifBlank { brandOf(p.addr) }.ifBlank { p.addr }.ifBlank { "Unknown" }
}

fun initialOf(s: String?): String = label(s).firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

fun hueOf(s: String?): Float = (abs(label(s).lowercase().hashCode()) % 360).toFloat()

fun fmtSize(n: Long): String =
    if (n >= 1_048_576) String.format(Locale.US, "%.1f MB", n / 1_048_576.0) else "${maxOf(1L, Math.round(n / 1024.0))} KB"

private val zone: ZoneId get() = ZoneId.systemDefault()

fun fullDate(ms: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(ms).atZone(zone))

/** Time for today's mail, "Nov 12" for this year, "12 Nov 2025" before that. */
fun fmtDate(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(zone)
    val now = LocalDate.now(zone)
    return when {
        d.toLocalDate() == now -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(d)
        d.year == now.year -> DateTimeFormatter.ofPattern("MMM d").format(d)
        else -> DateTimeFormatter.ofPattern("d MMM yyyy").format(d)
    }
}

fun dayLabel(ms: Long): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), LocalDate.now(zone))
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        else -> "Earlier"
    }
}

private val RE = RegexOption.IGNORE_CASE

// List preview: drop link clutter ("[https://cdn...]", bare URLs) from snippets, keeping readable text.
fun cleanSnippet(s: String?): String = (s ?: "")
    .replace(Regex("\\[([^\\]]+)\\]\\((?:https?:|cid:|mailto:)[^)]*\\)", RE), "$1")
    .replace(Regex("\\[\\s*(?:https?://|cid:)[^\\]]*\\]", RE), " ")
    .replace(Regex("\\[(?:image|img)\\b[^\\]]*\\]", RE), " ")
    .replace(Regex("<(?:https?://|mailto:)[^>]*>", RE), " ")
    .replace(Regex("(?:https?://|www\\.)\\S+", RE), " ")
    .replace(Regex("\\s+"), " ").trim()

fun previewOf(m: Mail): String {
    val c = cleanSnippet(m.snippet)
    if (c.isEmpty()) return "(no preview)"
    val first = Regex("^.*?[.!?](?=\\s|$)").find(c)
    return if (first != null && c.length > 140) first.value else c
}

fun escHtml(v: String): String = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Plain text of a mail (for quoting in a reply). */
fun plainOf(m: Mail): String {
    m.text?.let { if (it.isNotBlank()) return it.trim() }
    val h = m.html ?: return ""
    return h.replace(Regex("<(style|script)[^>]*>.*?</\\1>", setOf(RE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<br\\s*/?>|</p>|</div>|</tr>|</li>", RE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
}
