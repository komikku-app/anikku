#!/usr/bin/env python3
"""Patch miruro extension sources to remove problematic extractor deps."""

import os
import re
import sys

REMOVED_LIBS = ["m3u8server", "megacloudextractor", "omniembedextractor", "rapidcloudextractor"]


def patch_miruro_extractor(filepath):
    if not os.path.isfile(filepath):
        print("  Skipping: %s not found" % filepath)
        return False

    with open(filepath) as f:
        content = f.read()
    original = content

    # 1. Remove problematic imports
    for lib in REMOVED_LIBS:
        content = re.sub(
            r'^import aniyomi\.lib\.' + re.escape(lib) + r'\..*\n',
            '', content, flags=re.MULTILINE)

    # 2. Remove lazy vals (single-line and multi-line)
    content = re.sub(
        r'^    private val (embedExtractor|m3u8Integration) by lazy \{.*\}\n',
        r'    // [patched] \1 removed\n', content, flags=re.MULTILINE)
    content = re.sub(
        r'^    private val (megaCloudExtractor|rapidCloudExtractor) by lazy \{\n'
        r'        .*\n^    \}\n',
        r'    // [patched] \1 removed\n', content, flags=re.MULTILINE)

    # 3. Replace m3u8Integration usage block with fallback
    content = re.sub(
        r'        val proxied = m3u8Integration\.processVideoList\(videos\)\n'
        r'        Log\.d\(TAG, "parseStreamsFromResponse: built \${videos\.size} videos from \${sourcesDto\.streams\.size} streams, \${proxied\.size} proxied via m3u8server"\)\n'
        r'        return proxied\n',
        r'        Log.d(TAG, "parseStreamsFromResponse: built ${videos.size} videos (m3u8 proxying unavailable on JVM)")\n'
        r'        return videos\n',
        content, flags=re.MULTILINE)

    # 4. Comment out runCatching blocks for extractors (using .format() not f-strings to avoid brace issues)
    for ref in ["megaCloudExtractor", "embedExtractor"]:
        pat = (r'            }} -> runCatching \{\n'
               r'                ' + re.escape(ref) + r'\.getVideosFromUrl\(.*\n'
               r'(?:                    .*\n)*'
               r'                \)\n'
               r'            \}\.onFailure \{\n'
               r'(?:                .*\n)*'
               r'            \}\.getOrDefault\(.*\)\n')
        repl = (r'            }} -> \{\n'
                r'                // [patched] ' + ref + r' not available on JVM\n'
                r'                emptyList()\n'
                r'            }\n')
        content = re.sub(pat, repl, content, flags=re.MULTILINE)

    # rapidCloudExtractor single-line call
    pat = (r'            RAPID_CLOUD_HOSTS\.any \{\n'
           r'                lowerHost\.contains\(it\)\n'
           r'            \} -> runCatching \{\n'
           r'                rapidCloudExtractor\.getVideosFromUrl\(embedUrl, type = "Multi", name = qualityLabel\)\n'
           r'            \}\.onFailure \{\n'
           r'(?:                .*\n)*'
           r'            \}\.getOrDefault\(.*\)\n')
    repl = (r'            RAPID_CLOUD_HOSTS.any {\n'
            r'                lowerHost.contains(it)\n'
            r'            } -> {\n'
            r'                // [patched] rapidCloudExtractor not available on JVM\n'
            r'                emptyList()\n'
            r'            }\n')
    content = re.sub(pat, repl, content, flags=re.MULTILINE)

    # 5. Line-wide fallback: comment out lines with extractor refs.
    # Preserve already-patched lines so repeated builds do not stack comments.
    for ref in ["megaCloudExtractor", "rapidCloudExtractor", "embedExtractor", "m3u8Integration"]:
        pattern = re.compile(r'^(.*' + re.escape(ref) + r'\b.*)$', flags=re.MULTILINE)

        def comment_unpatched(match):
            line = match.group(1)
            return line if '[patched]' in line else '// [patched] ' + line

        content = pattern.sub(comment_unpatched, content)

    # 6. Handle orphaned named arguments after commented-out extractor calls.
    # Use line-by-line processing (not multi-line regex) for robustness.
    # Use re.search (not re.match) because lines may have leading whitespace
    # that prevents re.match from working.
    lines = content.split('\n')
    in_orphaned_block = False
    i = 0
    while i < len(lines):
        line = lines[i]
        # Detect an unprocessed [patched] line with getVideosFromUrl( or extractVideos(.
        if (not line.lstrip().startswith('// [patched]') and
                re.search(r'\[patched\].*(getVideosFromUrl|extractVideos)\(', line)):
            in_orphaned_block = True
            lines[i] = '// [patched] extractor call block removed'
            i += 1
            continue
        if in_orphaned_block:
            # Continue commenting out until we hit a line that doesn't match
            stripped = line.lstrip()
            if stripped.startswith(')') or re.match(r'[a-zA-Z]+ = .+,?', stripped):
                lines[i] = '// [patched] ' + line
                i += 1
                continue
            else:
                in_orphaned_block = False
        i += 1
    content = '\n'.join(lines)

    # 7. Final cleanup: catch any remaining orphaned named arguments or closing
    #    parens that were missed by the line-by-line scanner above. This handles
    #    edge cases where extractor variables were shadowed or renamed.
    lines = content.split('\n')
    for i in range(len(lines)):
        stripped = lines[i].lstrip()
        # Skip already-patched lines
        if stripped.startswith('// [patched]') or stripped.startswith('//'):
            continue
        # An orphaned named arg: `withM3u8Server = false,` or similar
        if re.match(r'withM3u8Server\s*=', stripped):
            lines[i] = '// [patched] ' + lines[i]
        # A standalone closing paren after a patched block (check previous line)
        elif stripped == ')' and i > 0:
            prev = lines[i-1].lstrip()
            if prev.startswith('// [patched]'):
                lines[i] = '// [patched] ' + lines[i]
    content = '\n'.join(lines)

    # 8. Post-cleanup: if extractPreRoutedEmbed has all branches patched out,
    #    the when-block is dead code with no return value. Replace the entire
    #    function body with `return emptyList()`. This handles the case where
    #    ALL extractors (megaCloud, rapidCloud, embed) were removed.
    #
    #    Match from 'return when {' to the closing braces of when + function
    #    (anchored to avoid matching EOF — Step 7 ensures orphans are cleaned).
    content = re.sub(
        r'(private fun extractPreRoutedEmbed\([^)]*\): List<Video> \{[^}]*?)\n'
        r'        return when \{.*?\n        \}\n    \}\n\}',
        r'\1\n        return emptyList()\n    }\n}',
        content, flags=re.DOTALL)
    # Fallback: if braces don't match exactly, nuke from 'return when {' to the
    # function's closing '    }' followed by class's '}' (end of file).
    # Only triggers if the function IS the last member of the class.
    # Safe: extractPreRoutedEmbed is always the last function in MiruroExtractor.
    if 'return when {' in content and 'extractPreRoutedEmbed' in content:
        # Find the function's closing pattern: 4-space indent '}' then class '}'
        m = re.search(
            r'(private fun extractPreRoutedEmbed\([^)]*\): List<Video> \{.*?lowerHost.*?\n)'
            r'(\s*return when \{.*)',
            content, flags=re.DOTALL)
        if m:
            content = m.group(1) + '        return emptyList()\n    }\n}'

    # 9. Clean up any literal backslash-brace artifacts from failed prior patches
    content = content.replace('\\}', '}')
    content = content.replace('\\{', '{')

    if content == original:
        print("  No changes needed for %s" % filepath)
        return False

    with open(filepath, "w") as f:
        f.write(content)

    changes = sum(1 for l in content.split('\n') if '[patched]' in l)
    print("  Patched: %s -- %d patch(es) applied" % (os.path.basename(filepath), changes))
    return True


def main():
    if len(sys.argv) < 2:
        print("Usage: patch-miruro-sources.py <extensions-source-dir>")
        sys.exit(1)

    miruro_dir = os.path.join(
        sys.argv[1], "src", "en", "miruro", "src",
        "eu", "kanade", "tachiyomi", "animeextension", "en", "miruro")
    if not os.path.isdir(miruro_dir):
        print("Miruro source dir not found: %s" % miruro_dir)
        sys.exit(1)

    patch_miruro_extractor(os.path.join(miruro_dir, "MiruroExtractor.kt"))


if __name__ == "__main__":
    main()
