#!/usr/bin/env python3
"""
Patch kissanime extension source code to add missing abstract members and fix
null-safety issues.

Usage:
    python3 patch-kissanime-source.py <kissanime_source_dir>
"""

import os
import sys


def main():
    if len(sys.argv) < 2:
        print("ERROR: Missing kissanime source directory argument")
        sys.exit(1)

    kissanime_dir = sys.argv[1]
    kissanime_file = os.path.join(
        kissanime_dir,
        "KissAnime.kt",
    )

    if not os.path.isfile(kissanime_file):
        print(f"ERROR: KissAnime.kt not found at {kissanime_file}")
        sys.exit(1)

    print(f"Patching kissanime in: {kissanime_file}")

    with open(kissanime_file, "r") as f:
        content = f.read()
    original = content

    # Step 1: Add hoster stubs before the companion object
    if "hosterListSelector" not in content:
        insert_marker = "companion object"
        if insert_marker in content:
            stub = (
                "    // ========================== Hoster stubs ============================\n"
                "    // (Not used - getVideoList is overridden directly)\n"
                "\n"
                '    override fun hosterListSelector(): String = ""\n'
                "\n"
                "    override fun hosterFromElement(element: org.jsoup.nodes.Element): "
                "eu.kanade.tachiyomi.animesource.model.Hoster = "
                "eu.kanade.tachiyomi.animesource.model.Hoster()\n"
                "\n"
            )
            content = content.replace(insert_marker, stub + insert_marker)
            print("  Added hosterListSelector and hosterFromElement stubs")
        else:
            print("  ERROR: Could not find 'companion object' in KissAnime.kt")

    # Step 2: Fix putString nullable key/entry issues
    # These occur in the PreferenceScreen setup where 'key' is String? but
    # SharedPreferences.Editor.putString expects non-null String
    content = content.replace(
        "preferences.edit().putString(key, entry).commit()",
        "preferences.edit().putString(key!!, entry!!).commit()",
    )
    print("  Patched: putString null-safety fixes (key!!, entry!!)")

    if content != original:
        with open(kissanime_file, "w") as f:
            f.write(content)
        print("  Kissanime patching complete")
    else:
        print("  NOTE: No changes needed - already patched")


if __name__ == "__main__":
    main()
