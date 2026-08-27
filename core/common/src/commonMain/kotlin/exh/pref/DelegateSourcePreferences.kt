package exh.pref

import tachiyomi.core.common.preference.PreferenceStore

class DelegateSourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun delegateSources() = preferenceStore.getBoolean("eh_delegate_sources", true)
}
