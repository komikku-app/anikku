#!/usr/bin/env python3
"""
Add missing hosterListSelector/hosterFromElement stubs to extensions that
extend ParsedAnimeHttpSource but override getVideoList directly.

Usage:
    python3 patch-hoster-stubs.py <extension_root_dir>
"""

import os
import sys


def find_main_source_file(ext_dir):
    """Find the main source file that extends ParsedAnimeHttpSource or AnimeHttpSource."""
    if not os.path.isdir(ext_dir):
        return None
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f.endswith(".kt"):
                fpath = os.path.join(root, f)
                try:
                    with open(fpath, "r") as fh:
                        content = fh.read()
                    if "ParsedAnimeHttpSource" in content or "AnimeHttpSource" in content:
                        return fpath
                except Exception:
                    continue
    return None


def needs_hoster_stubs(content):
    """Check if the extension needs hoster stubs."""
    # Check if it extends ParsedAnimeHttpSource but doesn't have hosterListSelector
    if "ParsedAnimeHttpSource" not in content:
        return False
    if "hosterListSelector" in content:
        return False  # Already has stubs
    return True


def main():
    if len(sys.argv) < 2:
        print("Usage: patch-hoster-stubs.py <ext_dir>")
        sys.exit(1)

    ext_dir = sys.argv[1]
    src_file = find_main_source_file(ext_dir)
    if not src_file:
        print(f"  No source file found in {ext_dir}")
        return

    with open(src_file, "r") as f:
        content = f.read()

    if not needs_hoster_stubs(content):
        return  # Already has stubs or doesn't need them

    # Find the class declaration to determine the full package
    ext_name = os.path.basename(ext_dir)
    print(f"  Adding hoster stubs to {os.path.basename(src_file)} ({ext_name})")

    # Insert stubs before the companion object or any import-like comment block
    insert_marker = None
    for marker in ["companion object", "private val DATE_FORMATTER", "private const val PREF_"]:
        if marker in content:
            insert_marker = marker
            break

    if not insert_marker:
        print(f"  ERROR: No suitable insertion point found in {src_file}")
        return

    stub = (
        "\n"
        "    // ========================== Hoster stubs ============================\n"
        "    // (Not used - getVideoList is overridden directly)\n"
        "\n"
        '    override fun hosterListSelector(): String = ""\n'
        "\n"
        "    override fun hosterFromElement(element: org.jsoup.nodes.Element): "
        "eu.kanade.tachiyomi.animesource.model.Hoster = "
        "eu.kanade.tachiyomi.animesource.model.Hoster()\n"
    )

    content = content.replace(insert_marker, stub + insert_marker)

    with open(src_file, "w") as f:
        f.write(content)
    print(f"  Added hosterListSelector and hosterFromElement stubs")


if __name__ == "__main__":
    main()
