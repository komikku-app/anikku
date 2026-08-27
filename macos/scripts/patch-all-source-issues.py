#!/usr/bin/env python3
"""
patch-all-source-issues.py

Universal patch script for yuzono/anime-extensions source compilation.
Fixes common issues that prevent JVM compilation of Android anime extensions.

Run BEFORE compiling extensions. Applies targeted in-place patches to
extension source files in the cloned git repo.

Usage:
    python3 patch-all-source-issues.py <extensions_source_dir>
"""

import os
import re
import sys


def log(msg):
    print(f"  [patch] {msg}")


def patch_file(fpath, old, new):
    """Replace old with new in file. Returns True if changed."""
    try:
        with open(fpath, "r") as fh:
            content = fh.read()
        if old in content:
            content = content.replace(old, new)
            with open(fpath, "w") as fh:
                fh.write(content)
            print(f"  [patch] Fixed: {os.path.basename(fpath)}")
            return True
        return False
    except Exception:
        return False


def regex_patch_file(fpath, pattern, replacement, description=""):
    """Apply regex replacement in file. Returns True if changed."""
    try:
        with open(fpath, "r") as fh:
            content = fh.read()
        new_content, count = re.subn(pattern, replacement, content)
        if count > 0:
            with open(fpath, "w") as fh:
                fh.write(new_content)
            if description:
                print(f"  [patch] {description}: {os.path.basename(fpath)} ({count} changes)")
            return True
        return False
    except Exception:
        return False


def patch_nullable_strings(ext_dir):
    """
    Fix #1: Nullable String? -> String mismatch.
    
    The MOST common failure (~12 extensions). Extensions pass nullable 
    `preferences.getString(key, default)` results to functions expecting 
    non-null String. The Android SharedPreferences.getString() returns 
    String? but our stub returns non-null "".

    Fix #1a: add !! to getString() calls when used as arguments or in assignments
    to non-null String variables.
    """
    fixes = 0
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if not f.endswith(".kt"):
                continue
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                new_content = content
                
                # Only fix if the file actually has a nullable-related error pattern.
                # Look for getString() used as function argument or RHS of assignment
                # where the result is used in non-null String context.
                # Pattern: .getString("...", "...") used in val/var declaration
                new_content = re.sub(
                    r'(val\s+\w+\s*=\s*preferences\??\.getString\([^)]*\))\s*$',
                    r'\1!!',
                    new_content,
                    flags=re.MULTILINE
                )
                new_content = re.sub(
                    r'(var\s+\w+\s*=\s*preferences\??\.getString\([^)]*\))\s*$',
                    r'\1!!',
                    new_content,
                    flags=re.MULTILINE
                )
                
                # Fix #1b: putString(key, <value>) where key is String?.
                # ListPreference.key is var key: String? = null in our Preference stubs.
                # Extensions pass this nullable key to SharedPreferences.Editor.putString.
                # The value can be a simple word (`entry`), a cast (`newValue as String`),
                # or any expression. We only add !! to the KEY argument.
                new_content = re.sub(
                    r'putString\((\w+),\s*',
                    lambda m: f'putString({m.group(1)}!!, ' if not m.group(1).endswith('!!') else m.group(0),
                    new_content,
                )
                
                # Fix #1c: putBoolean(key, value) where key is String?
                new_content = re.sub(
                    r'putBoolean\((\w+),\s*',
                    lambda m: f'putBoolean({m.group(1)}!!, ' if not m.group(1).endswith('!!') else m.group(0),
                    new_content,
                )
                
                # Fix #1d: Base64.decode(queryParams["key"], flags) where queryParams["key"] is String?
                # Pattern: Base64.decode(queryParams["key"], ...)
                new_content = re.sub(
                    r'Base64\.decode\(queryParams\["url"\]',
                    'Base64.decode(queryParams["url"]!!',
                    new_content,
                )
                
                if new_content != content:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    fixes += 1
            except Exception:
                continue
    return fixes


def patch_kisskh(ext_dir):
    """Fix #2: KissKH BuildConfig constants."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "KissKH.kt":
                fpath = os.path.join(root, f)
                changed = False
                changed |= patch_file(fpath, 
                    '"${BuildConfig.KISSKH_API}$id&version=2.8.10"',
                    '"https://api.kisskh.co/api/$id&version=2.8.10"')
                changed |= patch_file(fpath,
                    '"${BuildConfig.KISSKH_SUB_API}$id&version=2.8.10"',
                    '"https://subs.kisskh.co/api/$id&version=2.8.10"')
                if changed:
                    print("  [patch] KissKH.kt — replaced BuildConfig with hardcoded URLs")


def patch_av1encodes(ext_dir):
    """Fix #3: AV1Encodes Uri.decode -> java.net.URLDecoder.decode."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "AV1Encodes.kt":
                fpath = os.path.join(root, f)
                changed = patch_file(fpath, "Uri.decode", "java.net.URLDecoder.decode")
                if changed:
                    print("  [patch] AV1Encodes.kt — Uri.decode -> java.net.URLDecoder.decode")


def patch_animekhor(ext_dir):
    """Fix #4: AnimeKhor VidHideExtractor 1-arg -> 2-arg constructor.
    
    The locally compiled VidHideExtractor (from lib-extractors) uses a
    2-parameter constructor (client, headers) instead of the old 1-arg
    (client). Extensions written for the old API need headers added.
    """
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "AnimeKhor.kt":
                fpath = os.path.join(root, f)
                changed = patch_file(fpath,
                    "VidHideExtractor(client).videosFromUrl",
                    "VidHideExtractor(client, headers).videosFromUrl")
                if changed:
                    print("  [patch] AnimeKhor.kt — VidHideExtractor 1-arg -> 2-arg (added headers)")


def patch_animenosub(ext_dir):
    """Fix #5: Animenosub getEpisodeName override, videoSortPref."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "Animenosub.kt":
                fpath = os.path.join(root, f)
                changed = False
                # getEpisodeName doesn't exist in JVM source-api
                changed |= patch_file(fpath,
                    'override fun getEpisodeName(episode: SEpisode): String? = episode.name',
                    '// getEpisodeName removed for JVM')
                changed |= patch_file(fpath, 'videoSortPref', 'videoSortPref!!')
                if changed:
                    print("  [patch] Animenosub.kt — fixed overrides & nullables")


def patch_kimoitv(ext_dir):
    """Fix #6: KimoiTV missing abstract member stubs."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "KimoiTV.kt":
                fpath = os.path.join(root, f)
                try:
                    with open(fpath, "r") as fh:
                        content = fh.read()
                    if "hosterListSelector" in content or "hosterFromElement" in content:
                        continue
                    # Insert stubs before the last class closing brace
                    stubs = """
    // Host stubs (required by ParsedAnimeHttpSource on JVM)
    override fun hosterListSelector() = "ul.videos > li"
    override fun hosterFromElement(element: Element) = throw Exception("Not implemented")
"""
                    last_brace = content.rfind("}")
                    if last_brace > 0:
                        content = content[:last_brace] + stubs + content[last_brace:]
                        with open(fpath, "w") as fh:
                            fh.write(content)
                        print("  [patch] KimoiTV.kt — added hoster stubs")
                except Exception:
                    pass


def patch_miruro(ext_dir):
    """Fix #7: Miruro media.opt, setEnabled, JSONObject.NULL, getListPreference."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "Miruro.kt":
                fpath = os.path.join(root, f)
                changed = False
                new_content = None
                try:
                    with open(fpath, "r") as fh:
                        content = fh.read()
                    new_content = content
                    # Replace media?.opt("key") or media.opt("key") with null
                    # org.json.JSONObject.opt() AND get() are not available in our JVM classpath,
                    # even though other JSONObject methods work. These are on the fallback path
                    # for parseAnimeDetailsFromJsonObj — the primary path uses buildFromSnapshot/
                    # buildFromDto which correctly handle cover images.
                    # Passing null to extractCoverImage/extractBannerImage/extractMainStudio returns "".
                    new_content = re.sub(
                        r'media\??\.opt\("([^"]+)"\)',
                        r'null',
                        new_content,
                    )
                    
                    # Replace import for getListPreference with a comment
                    # getListPreference exists in Preferences.kt but silently fails to
                    # compile (the Kotlin compiler drops functions that reference
                    # unresolved types like Toast.makeText).
                    # We inline the function body directly.
                    new_content = new_content.replace(
                        'import keiyoushi.utils.getListPreference',
                        '// import keiyoushi.utils.getListPreference — inlined below'
                    )
                    
                    if new_content != content:
                        changed = True
                except Exception:
                    pass
                if changed and new_content:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print("  [patch] Miruro.kt — patched media.opt, getListPreference")


def patch_miruro_buildscript(ext_dir):
    """
    Fix #7b: Add getListPreference function to the keiyoushi-utils source.
    
    The function exists in Preferences.kt but is silently dropped during
    compilation (probably a Kotlin compiler issue with Java interop on
    certain method signatures). We extract it into its own file.
    """
    # Look for the keiyoushi-utils Preferences.kt in the core source
    prefs_path = None
    for root, dirs, files in os.walk(ext_dir):
        if "Preferences.kt" in files and "keiyoushi/utils" in root:
            prefs_path = os.path.join(root, "Preferences.kt")
            break
    
    if not prefs_path:
        return False
    
    try:
        with open(prefs_path, "r") as fh:
            content = fh.read()
        
        # Remove the getListPreference function definition from Preferences.kt
        # It silently gets dropped during compilation (kept the function causes
        # no compilation error but is somehow excluded from the class file).
        # We'll put it in its own file instead.
        import re as _re
        
        # Find and replace the getListPreference function (including its full body)
        # The function starts with 'fun PreferenceScreen.getListPreference(' and
        # ends with '    }\n}\n' (the closing brace of the apply block)
        pattern = r'fun PreferenceScreen\.getListPreference\s*\([^)]*\).*?^\}\n'
        new_content, count = _re.sub(pattern, '// getListPreference moved to separate file\n', content, flags=_re.MULTILINE | _re.DOTALL)
        
        if count > 0:
            with open(prefs_path, "w") as fh:
                fh.write(new_content)
            print(f"  [patch] Removed getListPreference from Preferences.kt ({count} occurrence)")
            
            # Now write the function to a new file in the same directory
            new_file = os.path.join(os.path.dirname(prefs_path), "GetListPreference.kt")
            
            # Check if file already exists from a previous run
            if os.path.isfile(new_file):
                print(f"  [patch] GetListPreference.kt already exists — skipping")
                return True
            
            getlist_code = """package keiyoushi.utils

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * Get a [ListPreference] preference
 *
 * @param key Preference key
 * @param default Default value for preference
 * @param title Preference title
 * @param summary Preference summary
 * @param entries Preference entries
 * @param entryValues Preference entry values
 * @param restartRequired Show restart required toast on preference change
 * @param onChange Run block on changed listener for validation, must return *true/false*
 * to determine if the preference change should be accepted
 * @param onComplete Run block on completion with text value as parameter
 */
fun PreferenceScreen.getListPreference(
    key: String,
    default: String,
    title: String,
    summary: String,
    entries: List<String>,
    entryValues: List<String>,
    restartRequired: Boolean = false,
    enabled: Boolean = true,
    onChange: (Preference, String) -> Boolean = { _, _ -> true },
    onComplete: (String) -> Unit = {},
): ListPreference = ListPreference(context).apply {
    this.key = key
    this.title = title
    this.summary = summary
    this.entries = entries.toTypedArray()
    this.entryValues = entryValues.toTypedArray()
    setDefaultValue(default)
    setEnabled(enabled)
    setOnPreferenceChangeListener { pref, newValue ->
        val value = newValue as String
        val isValid = onChange(pref, value)
        if (isValid) {
            onComplete(value)
        }
        isValid
    }
}
"""
            with open(new_file, "w") as fh:
                fh.write(getlist_code)
            print(f"  [patch] Created GetListPreference.kt in {os.path.dirname(prefs_path)}")
            return True
        else:
            print(f"  [patch] getListPreference not found in Preferences.kt — may already be removed")
            return False
    except Exception as e:
        print(f"  [patch] ERROR processing getListPreference in Preferences.kt: {e}")
        return False


def patch_cineby(ext_dir):
    """Fix #8: Cineby Android-only APIs (@RequiresApi, LruCache, video.copy(quality))."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                new_content = content
                changed = False
                
                # Remove @RequiresApi annotations (we have the stub now, but
                # some extensions use android.annotation.RequiresApi instead of
                # androidx.annotation.RequiresApi — remove both variants)
                new_content = re.sub(r'@RequiresApi\([^)]*\)\s*\n', '', new_content)
                if "import android.annotation.RequiresApi" in new_content:
                    new_content = new_content.replace(
                        "import android.annotation.RequiresApi", 
                        "// Removed for JVM: import android.annotation.RequiresApi"
                    )
                    changed = True
                if "import android.util.LruCache" in new_content:
                    new_content = new_content.replace(
                        "import android.util.LruCache",
                        "// We have android.util.LruCache stub"
                    )
                    changed = True
                
                # Fix video.copy(quality = ...) -> video.copy(videoTitle = ...)
                # quality is a computed getter (not a constructor param), so it
                # can't be used with copy(). videoTitle is the actual param.
                new_content = re.sub(
                    r'video\.copy\(quality\s*=\s*(\w+)\)',
                    r'video.copy(videoTitle = \1)',
                    new_content,
                )
                
                if new_content != content and changed:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print(f"  [patch] {f} — removed Android-only APIs, fixed copy(quality)")
                elif new_content != content:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print(f"  [patch] {f} — fixed video.copy(quality -> videoTitle)")
            except Exception:
                continue


def patch_kickassanime(ext_dir):
    """Fix #9: Kickassanime safe call violations on intent.data."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                new_content = content
                if "intent.data" in new_content and f.endswith("UrlActivity.kt"):
                    new_content = re.sub(r'(intent\.data)([^?!])', r'intent!!.data\2', new_content)
                    if new_content != content:
                        with open(fpath, "w") as fh:
                            fh.write(new_content)
                        print(f"  [patch] {f} — intent!!.data")
            except Exception:
                continue


def patch_missing_hosters(ext_path):
    """
    Generic fix: add hosterListSelector and hosterFromElement stubs.
    
    Many extensions extend ParsedAnimeHttpSource (via a multisrc theme like
    WcoTheme, AnikotoTheme, DooPlay, etc.) but only override getVideoList()
    directly, leaving hosterListSelector() and hosterFromElement() abstract.
    
    These methods are never called if getVideoList() is overridden, but the
    compiler requires them to be implemented since they're abstract in the
    ParsedAnimeHttpSource base class.
    """
    for root, dirs, files in os.walk(ext_path):
        for f in files:
            if not f.endswith(".kt"):
                continue
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                
                # Only patch files that have class declarations extending something
                if not re.search(r'class\s+\w+\s*:', content):
                    continue
                
                # Skip if already has hoster stubs
                if "hosterListSelector" in content or "hosterFromElement" in content:
                    continue
                
                # Check if this file extends ParsedAnimeHttpSource (directly or via
                # any multisrc theme). Use a regex check for multisrc imports to
                # catch ALL multisrc themes now and in the future without hardcoding.
                has_parsed_source = (
                    "ParsedAnimeHttpSource" in content or
                    re.search(r'import.*multisrc\.', content)
                )
                if not has_parsed_source:
                    continue
                
                # Insert stubs before the last class closing brace
                stubs = """
    // Host stubs (not used — video extraction via getVideoList override)
    override fun hosterListSelector() = "unused"
    override fun hosterFromElement(element: org.jsoup.nodes.Element) = eu.kanade.tachiyomi.animesource.model.Hoster("", "")
"""
                # Find the last } that's not inside a string or comment
                # Simple approach: find the last '}' at column 0 (class-level closing)
                lines = content.split('\n')
                last_brace_idx = -1
                for i in range(len(lines) - 1, -1, -1):
                    stripped = lines[i].strip()
                    if stripped == '}' or stripped.startswith('} //'):
                        last_brace_idx = i
                        break
                
                if last_brace_idx > 0:
                    lines.insert(last_brace_idx, stubs)
                    new_content = '\n'.join(lines)
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print(f"  [patch] {f} — added hoster stubs (before class brace)")
                else:
                    # No class body — class has supertype constructor call
                    # but no methods (no braces). E.g.:
                    #   class Anikoto : AnikotoTheme("en", ...)
                    # We need to ADD a class body with braces.
                    # Find the last line that might be part of the class
                    # declaration (e.g., a closing paren of supertype ctor).
                    # We insert '{' after the class declaration and add '}' at end.
                    lines = content.split('\n')
                    # Find last non-empty, non-whitespace line
                    last_code_line = -1
                    for i in range(len(lines) - 1, -1, -1):
                        if lines[i].strip():
                            last_code_line = i
                            break
                    if last_code_line >= 0:
                        # Add opening brace after the class declaration
                        lines[last_code_line] = lines[last_code_line] + ' {'
                        # Add stubs with closing brace at end
                        lines.append(stubs)
                        lines.append('}')
                        new_content = '\n'.join(lines)
                        with open(fpath, "w") as fh:
                            fh.write(new_content)
                        print(f"  [patch] {f} — added hoster stubs (no class body, added braces)")
            except Exception:
                continue


def patch_wco_override_fixes(ext_dir):
    """
    Fix #X: WcoTheme-related override fixes.
    
    Some wco* extensions override properties that don't exist in the
    current version of WcoTheme (e.g., disableRelatedAnimesBySearch).
    These properties were removed or renamed in newer versions.
    """
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if not f.endswith(".kt"):
                continue
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                new_content = content
                changed = False
                
                # Fix: remove 'override val disableRelatedAnimesBySearch'
                # This property was removed from WcoTheme — it doesn't exist
                # in the compiled lib-multisrc version.
                new_content = re.sub(
                    r'override\s+val\s+disableRelatedAnimesBySearch\s*=\s*(true|false)\s*\n',
                    r'// disableRelatedAnimesBySearch removed — not in WcoTheme base class\n',
                    new_content,
                )
                
                if new_content != content:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print(f"  [patch] {f} — removed disableRelatedAnimesBySearch override")
                    changed = True
            except Exception:
                continue


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 patch-all-source-issues.py <extensions_source_dir>")
        sys.exit(1)
    
    ext_root = sys.argv[1]
    if not os.path.isdir(ext_root):
        print(f"Error: {ext_root} is not a directory")
        sys.exit(1)
    
    print("")
    print("  ╔══════════════════════════════════════════════════════╗")
    print("  ║  Patching extension sources for JVM compilation     ║")
    print("  ╚══════════════════════════════════════════════════════╝")
    print("")
    
    # Patch core files (DataLifeEngine, DooPlay, DopeFlix — same as Step 1b)
    core_dirs = [
        os.path.join(ext_root, "lib-multisrc", "datalifeengine", "src"),
        os.path.join(ext_root, "lib-multisrc", "dooplay", "src"),
        os.path.join(ext_root, "lib-multisrc", "dopeflix", "src"),
    ]
    for cd in core_dirs:
        if os.path.isdir(cd):
            patch_nullable_strings(cd)
    
    # Apply patches to all extension directories
    src_dir = os.path.join(ext_root, "src")
    ext_count = 0
    if os.path.isdir(src_dir):
        for lang in sorted(os.listdir(src_dir)):
            lang_dir = os.path.join(src_dir, lang)
            if not os.path.isdir(lang_dir):
                continue
            for ext_name in sorted(os.listdir(lang_dir)):
                ext_path = os.path.join(lang_dir, ext_name, "src")
                if not os.path.isdir(ext_path):
                    continue
                ext_count += 1
                
                patch_nullable_strings(ext_path)
                patch_missing_hosters(ext_path)
                patch_wco_override_fixes(ext_path)
                patch_kisskh(ext_path)
                patch_av1encodes(ext_path)
                patch_animekhor(ext_path)
                patch_animenosub(ext_path)
                patch_kimoitv(ext_path)
                patch_miruro(ext_path)
                patch_miruro_buildscript(ext_path)
                patch_cineby(ext_path)
                patch_kickassanime(ext_path)
    
    print(f"")
    print(f"  Processed {ext_count} extension directories")
    
    # Also check patches applied at the core level
    core_utils_dir = os.path.join(ext_root, "core", "src", "main", "kotlin")
    if os.path.isdir(core_utils_dir):
        print(f"  Applying core-level patches: {core_utils_dir}")
        patch_miruro_buildscript(core_utils_dir)
    
    print(f"  ✅ Patching complete")
    print("")


if __name__ == "__main__":
    main()
