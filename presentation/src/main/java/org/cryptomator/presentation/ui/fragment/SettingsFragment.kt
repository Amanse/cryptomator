package org.cryptomator.presentation.ui.fragment

import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.service.PhotoContentJob
import org.cryptomator.presentation.ui.activity.SettingsActivity
import org.cryptomator.presentation.ui.dialog.DebugModeDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.DisableAppWhenObscuredDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.DisableSecureScreenDisclaimerDialog
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.file.LruFileCacheUtil
import timber.log.Timber
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE
import java.lang.String.format
import java.text.DecimalFormat
import kotlin.math.log10

class SettingsFragment : PreferenceFragmentCompat() {

	private lateinit var sharedPreferencesHandler: SharedPreferencesHandler

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		sharedPreferencesHandler = SharedPreferencesHandler(activity())
		addPreferencesFromResource(R.xml.preferences)
		isBiometricAuthenticationNotAvailableRemovePreference()
		setupAppVersion()
		setupLruCacheSize()
		setupLicense()
	}

	private val sendErrorReportClickListener = Preference.OnPreferenceClickListener {
		onSendErrorReportClicked()
		true
	}

	private val debugModeChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onDebugModeChanged(TRUE == newValue)
		true
	}

	private val clearCacheClickListener = Preference.OnPreferenceClickListener {
		LruFileCacheUtil(requireContext()).clear()
		setupLruCacheSize()
		true
	}

	private val updateCheckClickListener = Preference.OnPreferenceClickListener {
		onCheckUpdateClicked()
		true
	}

	private val useAutoPhotoUploadChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onUseAutoPhotoUploadChanged(TRUE == newValue)
		true
	}

	private val useLruChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		if (FALSE == newValue) {
			LruFileCacheUtil(requireContext()).clear()
			setupLruCacheSize()
		}

		Toast.makeText(context, context?.getString(R.string.screen_settings_lru_cache_changed__restart_toast), Toast.LENGTH_SHORT).show()

		true
	}

	private val disableAppWhenObscuredChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onDisableAppWhenObscuredChanged(TRUE == newValue)
		true
	}

	private val disableSecureScreenChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onDisableSecureScreenChanged(TRUE == newValue)
		true
	}

	private val screenStyleModeChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		sharedPreferencesHandler.setScreenStyleMode(newValue as String)
		AppCompatDelegate.setDefaultNightMode(sharedPreferencesHandler.screenStyleMode)
		activity().delegate.localNightMode = sharedPreferencesHandler.screenStyleMode
		true
	}

	private fun activity(): SettingsActivity = this.activity as SettingsActivity

	private fun isBiometricAuthenticationNotAvailableRemovePreference() {
		val biometricAuthenticationAvailable = BiometricManager.from(requireContext()).canAuthenticate()

		if (biometricAuthenticationAvailable != BiometricManager.BIOMETRIC_SUCCESS
				&& biometricAuthenticationAvailable != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
			val preference = findPreference(BIOMETRIC_AUTHENTICATION_ITEM_KEY)
			val generalCategory = findPreference(getString(R.string.screen_settings_section_general)) as PreferenceCategory?
			generalCategory?.removePreference(preference)

			Timber //
					.tag("SettingsFragment") //
					.d("No working biometric hardware detected")
		}
	}

	private fun setupAppVersion() {
		val preference = findPreference(APP_VERSION_ITEM_KEY)
		val versionName = SpannableString(BuildConfig.VERSION_NAME)
		versionName.setSpan( //
				ForegroundColorSpan(ContextCompat.getColor(activity(), R.color.textColorLight)), //
				0, versionName.length, 0)
		preference?.summary = versionName
	}

	private fun setupLruCacheSize() {
		val preference = findPreference(DISPLAY_LRU_CACHE_SIZE_ITEM_KEY)

		val size = LruFileCacheUtil(requireContext()).totalSize()

		val readableSize: String = if (size > 0) {
			val units = arrayOf("B", "KB", "MB", "GB", "TB")
			val unitIndex = (log10(size.toDouble()) / 3).toInt()
			val unitValue = (1 shl unitIndex * 10).toDouble()

			(DecimalFormat("#,##0.#")
					.format(size / unitValue) + " "
					+ units[unitIndex])
		} else {
			"0 B"
		}

		val lruCacheSize = SpannableString(readableSize)

		lruCacheSize.setSpan( //
				ForegroundColorSpan(ContextCompat.getColor(activity(), R.color.textColorLight)), //
				0, lruCacheSize.length, 0)
		preference?.summary = lruCacheSize
	}

	private fun setupLicense() {
		when (BuildConfig.FLAVOR) {
			"apkstore" -> {
				findPreference(SharedPreferencesHandler.MAIL)?.title = format(getString(R.string.screen_settings_license_mail), sharedPreferencesHandler.mail())
				setupUpdateCheck()
			}
			"fdroid" -> {
				findPreference(SharedPreferencesHandler.MAIL)?.title = format(getString(R.string.screen_settings_license_mail), sharedPreferencesHandler.mail())
				removeUpdateCheck()
			}
			else -> {
				preferenceScreen.removePreference(findPreference(LICENSE_ITEM_KEY))
				removeUpdateCheck()
			}
		}
	}

	private fun removeUpdateCheck() {
		val versionCategory = findPreference("versionCategory") as PreferenceCategory?
		versionCategory?.removePreference(findPreference(UPDATE_CHECK_ITEM_KEY))
		versionCategory?.removePreference(findPreference(UPDATE_INTERVAL_ITEM_KEY))
	}

	fun setupUpdateCheck() {
		val preference = findPreference(UPDATE_CHECK_ITEM_KEY)

		val lastUpdateCheck = sharedPreferencesHandler.lastUpdateCheck()
		val readableDate: String = if (lastUpdateCheck != null) {
			val dateFormatUser = android.text.format.DateFormat.getLongDateFormat(context)
			val strDate: String = dateFormatUser.format(lastUpdateCheck)
			format(getString(R.string.screen_settings_last_check_updates), strDate)
		} else {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getString(R.string.screen_settings_last_check_updates_never)
			} else {
				getString(R.string.screen_settings_last_check_updates_never_pre_marshmallow)
			}
		}

		val date = SpannableString(readableDate)

		date.setSpan( //
				ForegroundColorSpan(ContextCompat.getColor(activity(), R.color.textColorLight)), //
				0, date.length, 0)
		preference?.summary = date
	}

	override fun onResume() {
		super.onResume()
		findPreference(SEND_ERROR_REPORT_ITEM_KEY)?.onPreferenceClickListener = sendErrorReportClickListener
		findPreference(LRU_CACHE_CLEAR_ITEM_KEY)?.onPreferenceClickListener = clearCacheClickListener
		findPreference(SharedPreferencesHandler.DEBUG_MODE)?.onPreferenceChangeListener = debugModeChangedListener
		findPreference(SharedPreferencesHandler.DISABLE_APP_WHEN_OBSCURED)?.onPreferenceChangeListener = disableAppWhenObscuredChangedListener
		findPreference(SharedPreferencesHandler.SECURE_SCREEN)?.onPreferenceChangeListener = disableSecureScreenChangedListener
		findPreference(SharedPreferencesHandler.SCREEN_STYLE_MODE)?.onPreferenceChangeListener = screenStyleModeChangedListener
		findPreference(SharedPreferencesHandler.PHOTO_UPLOAD)?.onPreferenceChangeListener = useAutoPhotoUploadChangedListener
		findPreference(SharedPreferencesHandler.USE_LRU_CACHE)?.onPreferenceChangeListener = useLruChangedListener
		findPreference(SharedPreferencesHandler.LRU_CACHE_SIZE)?.onPreferenceChangeListener = useLruChangedListener
		if (BuildConfig.FLAVOR == "apkstore") {
			findPreference(UPDATE_CHECK_ITEM_KEY)?.onPreferenceClickListener = updateCheckClickListener
		}
	}

	fun deactivateDebugMode() {
		sharedPreferencesHandler.setDebugMode(false)
		(findPreference(SharedPreferencesHandler.DEBUG_MODE) as SwitchPreferenceCompat?)?.isChecked = false
	}

	fun disableAppWhenObscured() {
		sharedPreferencesHandler.setDisableAppWhenObscured(true)
		(findPreference(SharedPreferencesHandler.DISABLE_APP_WHEN_OBSCURED) as SwitchPreferenceCompat?)?.isChecked = true
	}

	fun secureScreen() {
		sharedPreferencesHandler.setSecureScreen(true)
		(findPreference(SharedPreferencesHandler.SECURE_SCREEN) as SwitchPreferenceCompat?)?.isChecked = true
	}

	private fun onSendErrorReportClicked() {
		activity().presenter().onSendErrorReportClicked()
	}

	private fun onCheckUpdateClicked() {
		activity().presenter().onCheckUpdateClicked()
	}

	private fun onDebugModeChanged(enabled: Boolean) {
		if (enabled) {
			activity().showDialog(DebugModeDisclaimerDialog.newInstance())
		} else {
			activity().presenter().onDebugModeChanged(false)
		}
	}

	private fun onDisableAppWhenObscuredChanged(enabled: Boolean) {
		if (!enabled) {
			activity().showDialog(DisableAppWhenObscuredDisclaimerDialog.newInstance())
		}
	}

	private fun onDisableSecureScreenChanged(enabled: Boolean) {
		if (!enabled) {
			activity().showDialog(DisableSecureScreenDisclaimerDialog.newInstance())
		}
	}

	private fun onUseAutoPhotoUploadChanged(enabled: Boolean) {
		if (enabled) {
			activity().grantLocalStoragePermissionForAutoUpload()
		} else {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				PhotoContentJob.cancelJob(activity().applicationContext)
			}
		}
		(findPreference(SharedPreferencesHandler.PHOTO_UPLOAD) as SwitchPreferenceCompat?)?.isChecked = enabled
	}

	fun rootView(): View {
		return activity().findViewById(R.id.activityRootView)
	}

	fun disableAutoUpload() {
		onUseAutoPhotoUploadChanged(false)
	}

	companion object {
		private const val APP_VERSION_ITEM_KEY = "appVersion"
		private const val SEND_ERROR_REPORT_ITEM_KEY = "sendErrorReport"
		private const val BIOMETRIC_AUTHENTICATION_ITEM_KEY = "biometricAuthentication"
		private const val LICENSE_ITEM_KEY = "license"
		private const val UPDATE_CHECK_ITEM_KEY = "updateCheck"
		private const val UPDATE_INTERVAL_ITEM_KEY = "updateInterval"
		private const val DISPLAY_LRU_CACHE_SIZE_ITEM_KEY = "displayLruCacheSize"
		private const val LRU_CACHE_CLEAR_ITEM_KEY = "lruCacheClear"
	}

}
