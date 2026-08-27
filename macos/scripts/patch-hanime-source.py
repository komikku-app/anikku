#!/usr/bin/env python3
"""
Patch hanime extension source code to remove Chicory/WASM/WebView dependencies.

Removes files that depend on unavailable JVM dependencies and patches
Hanime.kt to use NativeSignatureProvider exclusively.

Usage:
    python3 patch-hanime-source.py <hanime_source_dir>
"""

import os
import re
import sys


def main():
    if len(sys.argv) < 2:
        hanime_dir = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "tmp",
            "anikku-batch-source-build",
            "extensions-source",
            "src",
            "en",
            "hanime",
            "src",
            "eu",
            "kanade",
            "tachiyomi",
            "animeextension",
            "en",
            "hanime",
        )
    else:
        hanime_dir = sys.argv[1]

    if not os.path.isdir(hanime_dir):
        print(f"ERROR: Directory not found: {hanime_dir}")
        sys.exit(1)

    print(f"Patching hanime in: {hanime_dir}")

    # Step 1: Remove problematic files
    files_to_remove = [
        "ChicoryGlue.kt",
        "ChicorySignatureProvider.kt",
        "HanimeWasmBinary.kt",
        "WebViewSignatureProvider.kt",
    ]
    for fname in files_to_remove:
        fpath = os.path.join(hanime_dir, fname)
        if os.path.isfile(fpath):
            os.remove(fpath)
            print(f"  Removed: {fname}")

    # Step 2: Patch Hanime.kt
    hanime_file = os.path.join(hanime_dir, "Hanime.kt")
    if not os.path.isfile(hanime_file):
        print(f"  WARNING: Hanime.kt not found at {hanime_file}")
        return

    with open(hanime_file, "r") as f:
        content = f.read()
    original = content

    # Replace "webview" -> WebViewSignatureProvider()
    content = content.replace(
        '"webview" -> WebViewSignatureProvider()',
        '"webview" -> NativeSignatureProvider()',
    )

    # Replace the "wasm" -> { ... } block with just NativeSignatureProvider
    # Find the wasm block by looking for "wasm" -> {
    wasm_start = content.find('"wasm" -> {')
    if wasm_start >= 0:
        # Find the matching closing brace
        brace_count = 0
        in_block = False
        end_pos = wasm_start
        for i in range(wasm_start, len(content)):
            ch = content[i]
            if ch == "{":
                brace_count += 1
                in_block = True
            elif ch == "}":
                brace_count -= 1
                if in_block and brace_count == 0:
                    end_pos = i + 1
                    break
        if end_pos > wasm_start:
            # Check indentation of the wasm line
            indent = ""
            for j in range(wasm_start - 1, -1, -1):
                if content[j] == "\n":
                    indent = content[j + 1 : wasm_start]
                    break
                if not content[j].isspace():
                    break
            content = (
                content[:wasm_start]
                + f'"wasm" -> NativeSignatureProvider()'
                + content[end_pos:]
            )
            print(f"  Patched: replaced WASM block with NativeSignatureProvider")

    # Replace WebView fallback messages with NativeSignatureProvider
    content = content.replace(
        "falling back to WebViewSignatureProvider",
        "falling back to NativeSignatureProvider",
    )

    # Replace the else-branch return value (WebViewSignatureProvider() -> NativeSignatureProvider())
    # This handles the case in the else block where WebViewSignatureProvider() is still used
    # after the log message was already replaced above.
    # We look for the specific pattern: Log.w(..., "...")\n            WebViewSignatureProvider()
    # and replace the WebViewSignatureProvider() call with NativeSignatureProvider()
    content = re.sub(
        r'(Log\.w\([^)]+\)[^}]*?)WebViewSignatureProvider\(\)',
        r'\1NativeSignatureProvider()',
        content,
    )
    print(f"  Patched: else-branch fallback → NativeSignatureProvider")

    # Replace any remaining standalone WebViewSignatureProvider() calls
    content = content.replace("WebViewSignatureProvider()", "NativeSignatureProvider()")

    # Replace SIG_PROVIDER_LIST to only have native
    content = re.sub(
        r'arrayOf\("native",\s*"webview",\s*"wasm"\)',
        'arrayOf("native")',
        content,
    )
    print(f"  Patched: SIG_PROVIDER_LIST → only native")

    # Replace preference entries to only show native option
    content = content.replace(
        '"Direct SHA-256 computation (Recommended)", "WebView", "Chicory WASM Runtime (Experimental)"',
        '"Direct SHA-256 computation (Recommended)"',
    )
    print(f"  Patched: preference entries → native only")

    if content != original:
        with open(hanime_file, "w") as f:
            f.write(content)
        print(f"  Patched: Hanime.kt — removed Chicory/WebView/WASM references")
    else:
        print(f"  NOTE: No changes needed — already patched")


if __name__ == "__main__":
    main()
