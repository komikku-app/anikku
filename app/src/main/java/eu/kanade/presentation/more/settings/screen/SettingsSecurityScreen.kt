package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsSecurityScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val privacyPreferences = remember { Injekt.get<PrivacyPreferences>() }
        return listOf(
            getSecurityGroup(securityPreferences),
            getFirebaseGroup(privacyPreferences),
        )
    }

    @Composable
    private fun getSecurityGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val authSupported = remember { context.isAuthenticationSupported() }
        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AMR.strings.pref_security),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useAuthPref,
                    title = stringResource(MR.strings.lock_with_biometrics),
                    enabled = authSupported,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_with_biometrics),
                        )
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.lockAppAfter(),
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.lock_when_idle),
                    enabled = authSupported && useAuth,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_when_idle),
                        )
                    },
                ),

                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.hideNotificationContent(),
                    title = stringResource(MR.strings.hide_notification_content),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.secureScreen(),
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.secure_screen),
                ),
                kotlin.run {
                    val navigator = LocalNavigator.currentOrThrow
                    val count by securityPreferences.authenticatorTimeRanges().collectAsState()
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(SYMR.strings.action_edit_biometric_lock_times),
                        subtitle = pluralStringResource(
                            SYMR.plurals.num_lock_times,
                            count.size,
                            count.size,
                        ),
                        enabled = useAuth,
                        onClick = {
                            navigator.push(BiometricTimesScreen())
                        },
                    )
                },
                kotlin.run {
                    val selection by securityPreferences.authenticatorDays().collectAsState()
                    var dialogOpen by remember { mutableStateOf(false) }
                    if (dialogOpen) {
                        SetLockedDaysDialog(
                            onDismissRequest = { dialogOpen = false },
                            initialSelection = selection,
                            onDaysSelected = {
                                dialogOpen = false
                                securityPreferences.authenticatorDays().set(it)
                            },
                        )
                    }
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(SYMR.strings.biometric_lock_days),
                        subtitle = stringResource(SYMR.strings.biometric_lock_days_summary),
                        enabled = useAuth,
                        onClick = { dialogOpen = true },
                    )
                },
                // SY <--
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
            ),
        )
    }

    @Composable
    private fun getFirebaseGroup(
        privacyPreferences: PrivacyPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AMR.strings.pref_firebase),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.crashlytics(),
                    title = stringResource(AMR.strings.onboarding_permission_crashlytics),
                    subtitle = stringResource(AMR.strings.onboarding_permission_crashlytics_description),
                ),
                /*
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.analytics(),
                    title = stringResource(MR.strings.onboarding_permission_analytics),
                    subtitle = stringResource(MR.strings.onboarding_permission_analytics_description),
                ),
                 */
                Preference.PreferenceItem.InfoPreference(stringResource(AMR.strings.firebase_summary)),
            ),
        )
    }

    // SY -->
    enum class DayOption(val day: Int, val stringRes: StringResource) {
        Sunday(SecureActivityDelegate.LOCK_SUNDAY, SYMR.strings.sunday),
        Monday(SecureActivityDelegate.LOCK_MONDAY, SYMR.strings.monday),
        Tuesday(SecureActivityDelegate.LOCK_TUESDAY, SYMR.strings.tuesday),
        Wednesday(SecureActivityDelegate.LOCK_WEDNESDAY, SYMR.strings.wednesday),
        Thursday(SecureActivityDelegate.LOCK_THURSDAY, SYMR.strings.thursday),
        Friday(SecureActivityDelegate.LOCK_FRIDAY, SYMR.strings.friday),
        Saturday(SecureActivityDelegate.LOCK_SATURDAY, SYMR.strings.saturday),
    }

    @Composable
    fun SetLockedDaysDialog(
        onDismissRequest: () -> Unit,
        initialSelection: Int,
        onDaysSelected: (Int) -> Unit,
    ) {
        val selected = remember(initialSelection) {
            DayOption.entries.filter { it.day and initialSelection == it.day }
                .toMutableStateList()
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(SYMR.strings.biometric_lock_days)) },
            text = {
                LazyColumn {
                    DayOption.entries.forEach { day ->
                        item {
                            val isSelected = selected.contains(day)
                            val onSelectionChanged = {
                                when (!isSelected) {
                                    true -> selected.add(day)
                                    false -> selected.remove(day)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectionChanged() },
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onSelectionChanged() },
                                )
                                Text(
                                    text = stringResource(day.stringRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        onDaysSelected(
                            selected.fold(0) { i, day ->
                                i or day.day
                            },
                        )
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

private val LockAfterValues = persistentListOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
