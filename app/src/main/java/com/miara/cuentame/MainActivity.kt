package com.miara.cuentame

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.app.ui.AppPreferencesState
import com.miara.cuentame.app.ui.AppViewModel
import com.miara.cuentame.app.ui.CuentameApp
import com.miara.cuentame.app.ui.theme.CuentameTheme
import com.miara.cuentame.core.preferences.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private val viewModel: AppViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val prefState by viewModel.preferencesState.collectAsStateWithLifecycle()
            
            val preferences = (prefState as? AppPreferencesState.Ready)?.preferences

            LaunchedEffect(preferences?.appLocaleTag) {
                preferences?.appLocaleTag?.let { tag ->
                    val appLocales = LocaleListCompat.forLanguageTags(tag)
                    if (AppCompatDelegate.getApplicationLocales() != appLocales) {
                        AppCompatDelegate.setApplicationLocales(appLocales)
                    }
                }
            }

            CuentameTheme(
                darkTheme = when (preferences?.themeMode) {
                    ThemeMode.SYSTEM, null -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = preferences?.dynamicColorEnabled ?: true
            ) {
                CuentameApp(windowSizeClass = windowSizeClass)
            }
        }
    }
}
