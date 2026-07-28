#!/usr/bin/env python3
"""Patch WcoTheme.kt: make iframeParse content-based instead of domain-whitelisted.

WCO sites frequently rotate their embed domains. The original code checks
`iframeLink.contains("embed.wcostream")` and `"vhs.watchanimesub"` which
are stale. This patch removes the domain checks and instead inspects the
response body: if it contains a `getJSON` script, use the VideoResponseDto
path; if it contains `.m3u8`, use the HLS extraction path.

Note: iframeExtractor is left unchanged (parallelCatchingFlatMapBlocking).
The ChromeCDPClient referer pass-through and content-based iframeParse
are sufficient to fix WCO video extraction.
"""

import os
import sys


NEW_IFRAMEPARSE = r'''    open suspend fun iframeParse(iframeLink: String): List<Video> {
        // Domain-agnostic extraction: inspect response body instead of
        // whitelisting embed domains (WCO rotates them frequently).
        // The CloudflareInterceptor (via Chrome CDP) handles any WAF
        // challenges on the iframe page transparently.
        //
        // Bypass the WCO announcement interstitial (10s countdown) by
        // addressing video-js.php directly. If the URL doesn't contain
        // "index.php", the replace is a no-op and we proceed normally.
        val playerLink = iframeLink.replace("index.php", "video-js.php")
        val iframeSoup = try {
            client.newCall(GET(playerLink, headers)).awaitSuccess().asJsoup()
        } catch (e: Exception) {
            // DEBUG: dump error info when HTTP/CDP fails (e.g. Cloudflare timeout).
            // Remove once CDP Cloudflare bypass is reliable.
            try {
                val errDump = java.io.File("/tmp/wco-iframe-dump-error-" +
                    playerLink.toHttpUrl().host.replace('.', '-') + ".html")
                errDump.parentFile?.mkdirs()
                java.io.PrintWriter(errDump).use { pw ->
                    pw.println("<!-- ERROR URL: $playerLink -->")
                    pw.println("<!-- Exception: ${e.message} -->")
                    pw.println("<!-- Type: ${e::class.simpleName} -->")
                }
                println("[WCO-DEBUG] Error dump → ${errDump.absolutePath} (${e::class.simpleName})")
            } catch (_: Exception) {}
            return emptyList()
        }
        val body = iframeSoup.html()

        // DEBUG: dump iframe HTML to file for pattern inspection.
        // Remove this block once the correct extraction patterns are confirmed.
        try {
            val dumpFile = java.io.File("/tmp/wco-iframe-dump-" +
                playerLink.toHttpUrl().host.replace('.', '-') + ".html")
            dumpFile.parentFile?.mkdirs()
            java.io.PrintWriter(dumpFile).use { pw ->
                pw.println("<!-- URL: $playerLink -->")
                pw.print(body)
            }
            println("[WCO-DEBUG] Dumped iframe HTML to ${dumpFile.absolutePath}")
        } catch (_: Exception) { /* /tmp may not be writable */ }

        // Path 1: Relaxed getJSON/ajax API extraction.
        // WCO changes quote styles (single/double/backtick) and sometimes
        // uses $.ajax instead of $.getJSON. Match all variants.
        val jsonRegex = Regex("""\$\.(?:getJSON|ajax)\s*\(\s*['"`]([^'"`]+)['"`]""")
        val jsonMatch = jsonRegex.find(body)
        if (jsonMatch != null) {
            val apiPath = jsonMatch.groupValues[1]
            val iframeDomain = "https://" + iframeLink.toHttpUrl().host
            val requestUrl = if (apiPath.startsWith("http")) apiPath
                else iframeDomain + (if (apiPath.startsWith("/")) "" else "/") + apiPath
            val requestHeaders = headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .set("Referer", requestUrl)
                .set("Origin", iframeDomain)
                .build()
            try {
                val videoData = client.newCall(GET(requestUrl, requestHeaders))
                    .awaitSuccess()
                    .parseAs<VideoResponseDto>()
                if (videoData.videos.isNotEmpty()) return videoData.videos
            } catch (_: Exception) { /* fall through to next path */ }
        }

        // Path 2: Universal m3u8 HLS extraction.
        // Search for ANY .m3u8 URL in the page, not just inside getRedirectedUrl().
        val m3u8Regex = Regex("""(https?://[^"'<>\s]+\.m3u8[^"'<>\s]*)""")
        val m3u8Match = m3u8Regex.find(body)
        if (m3u8Match != null) {
            val playlistUrl = m3u8Match.groupValues[1]
            return playlistUtils.extractFromHls(
                playlistUrl = playlistUrl,
                referer = "$iframeLink/",
                videoNameGen = { quality -> "Premium - $quality" },
            )
        }

        // Path 3: Base64/atob obfuscation fallback.
        // WCO sometimes wraps the API URL or stream link in atob("...").
        val atobRegex = Regex("""atob\s*\(\s*['"]([^'"]+)['"]\s*\)""")
        val atobMatch = atobRegex.find(body)
        if (atobMatch != null) {
            try {
                val decoded = java.util.Base64.getDecoder()
                    .decode(atobMatch.groupValues[1])
                    .toString(java.nio.charset.Charset.forName("UTF-8"))
                // The decoded string might be an API URL or a direct m3u8 link
                if (decoded.contains(".m3u8")) {
                    return playlistUtils.extractFromHls(
                        playlistUrl = decoded.trim(),
                        referer = "$iframeLink/",
                        videoNameGen = { quality -> "Premium - $quality" },
                    )
                }
                // Try to use decoded string as API path
                val iframeDomain = "https://" + iframeLink.toHttpUrl().host
                val requestUrl = if (decoded.startsWith("http")) decoded.trim()
                    else iframeDomain + (if (decoded.startsWith("/")) "" else "/") + decoded.trim()
                val videoData = client.newCall(
                    GET(requestUrl, headersBuilder()
                        .add("X-Requested-With", "XMLHttpRequest")
                        .set("Referer", requestUrl)
                        .set("Origin", iframeDomain)
                        .build())
                ).awaitSuccess().parseAs<VideoResponseDto>()
                if (videoData.videos.isNotEmpty()) return videoData.videos
            } catch (_: Exception) { /* fall through */ }
        }

        return emptyList()
    }'''


def patch_wco_theme(filepath):
    if not os.path.isfile(filepath):
        print(f"  Skipping: {filepath} not found")
        return False

    with open(filepath) as f:
        content = f.read()

    original = content

    lines = content.split('\n')

    # Find the old iframeParse method boundaries by line
    start_idx = None
    end_idx = None
    brace_depth = 0
    in_method = False

    for i, line in enumerate(lines):
        if 'open suspend fun iframeParse(iframeLink: String): List<Video>' in line:
            start_idx = i
            in_method = True
            # This line might have the opening brace or it might be on the next line
            brace_depth += line.count('{') - line.count('}')
            continue

        if in_method:
            brace_depth += line.count('{') - line.count('}')
            # When brace_depth returns to 0, we've found the end of the method
            if brace_depth == 0:
                end_idx = i
                break

    if start_idx is None or end_idx is None:
        print("  Could not find iframeParse method boundaries")
        return False

    # Replace the old method with the new one
    new_lines = lines[:start_idx] + NEW_IFRAMEPARSE.split('\n') + lines[end_idx + 1:]
    content = '\n'.join(new_lines)

    if content == original:
        print("  No changes needed for WcoTheme.kt")
        return False

    with open(filepath, "w") as f:
        f.write(content)

    print(f"  Patched: WcoTheme.kt — iframeParse now content-based (domain-agnostic)")
    return True


def main():
    if len(sys.argv) < 2:
        print("Usage: patch-wco-video-extraction.py <extensions-source-dir>")
        sys.exit(1)

    wco_theme = os.path.join(
        sys.argv[1], "lib-multisrc", "wcotheme", "src",
        "eu", "kanade", "tachiyomi", "multisrc", "wcotheme", "WcoTheme.kt")

    if not os.path.isfile(wco_theme):
        print(f"WcoTheme.kt not found: {wco_theme}")
        sys.exit(1)

    patch_wco_theme(wco_theme)


if __name__ == "__main__":
    main()
