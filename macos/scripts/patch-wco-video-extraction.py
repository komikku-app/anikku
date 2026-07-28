#!/usr/bin/env python3
"""Patch WcoTheme.kt: make iframeParse content-based instead of domain-whitelisted.

WCO sites frequently rotate their embed domains. The original code checks
`iframeLink.contains("embed.wcostream")` and `"vhs.watchanimesub"` which
are stale. This patch removes the domain checks and instead inspects the
response body: if it contains a `getJSON` script, use the VideoResponseDto
path; if it contains `.m3u8`, use the HLS extraction path.
"""

import os
import sys


NEW_IFRAMEPARSE = '''    open suspend fun iframeParse(iframeLink: String): List<Video> {
        // Domain-agnostic extraction: inspect response body instead of
        // whitelisting embed domains (WCO rotates them frequently).
        // The CloudflareInterceptor (via Chrome CDP) handles any WAF
        // challenges on the iframe page transparently.
        val iframeSoup = client.newCall(GET(iframeLink, headers))
            .awaitSuccess().asJsoup()

        // Path 1: getJSON-based extraction (formerly embed.wcostream)
        val getVideoLinkScript =
            iframeSoup.selectFirst("script:containsData(getJSON)")?.data()
        if (getVideoLinkScript != null) {
            val getVideoLink =
                getVideoLinkScript.substringAfter("$.getJSON(\\"").substringBefore("\\"")

            val iframeDomain = "https://" + iframeLink.toHttpUrl().host
            val requestUrl = iframeDomain + getVideoLink

            val requestHeaders = headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .set("Referer", requestUrl)
                .set("Origin", iframeDomain)
                .build()

            val videoData = client.newCall(GET(requestUrl, requestHeaders))
                .awaitSuccess()
                .parseAs<VideoResponseDto>()

            return videoData.videos
        }

        // Path 2: m3u8 HLS extraction (formerly vhs.watchanimesub)
        val body = iframeSoup.html()
        val matchResult = Regex("""getRedirectedUrl\\("(https://[\\\\w-/.]+/index\\\\.m3u8)"""").find(body)
        if (matchResult != null) {
            val playlistUrl = matchResult.groupValues[1]
            return playlistUtils.extractFromHls(
                playlistUrl = playlistUrl,
                referer = "$iframeLink/",
                videoNameGen = { quality -> "Premium - $quality" },
            )
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
