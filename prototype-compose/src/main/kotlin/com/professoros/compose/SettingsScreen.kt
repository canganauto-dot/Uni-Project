package com.professoros.compose

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.professoros.model.ThemeMode
import com.professoros.service.ThemePreferences
import kotlinx.coroutines.*
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/** Appearance uses the existing atomic local preference file; academic storage is unchanged. */
class AppearanceSettings(val database:Path, private val scope:CoroutineScope) {
    val preferenceFile:Path = database.resolveSibling("appearance.properties")
    private val preferences = ThemePreferences(preferenceFile)
    var mode by mutableStateOf(preferences.load()); private set
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    fun select(next:ThemeMode) {
        if(saving || next==mode)return
        saving=true;error=null
        scope.launch {
            try { withContext(Dispatchers.IO){preferences.save(next)};mode=next }
            catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){
                Logger.getLogger("ComposeSettings").log(Level.WARNING,"Cannot save appearance",failure)
                error="Appearance could not be saved. Check that the data folder is writable."
            } finally {saving=false}
        }
    }
}

@Composable internal fun SettingsScreen(settings:AppearanceSettings, openTerms:()->Unit) {
    Column(Modifier.fillMaxWidth().testTag("settings-page"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Copy("Appearance",20)
            Copy("A considered workspace, in your preferred light. Changes keep your current work in place.",13,Ink.muted)
            Toolbar {
                ThemeMode.entries.forEach {mode->
                    Action(mode.name.lowercase().replaceFirstChar {it.uppercase()},"settings",active=settings.mode==mode,
                        enabled=!settings.saving,modifier=Modifier.testTag("theme-${mode.name}").semantics {selected=settings.mode==mode}){settings.select(mode)}
                }
            }
            Copy(if(settings.saving)"Saving appearance…" else "${settings.mode} · saved on this device",12,Ink.muted)
            Copy("System follows your desktop appearance. Your selection is remembered when you reopen Professor OS.",12,Ink.muted)
            settings.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("settings-error"))}
        }
        Panel(Modifier.fillMaxWidth()) {
            Copy("Local storage",20)
            Copy("Your academic workspace stays on this device.",13,Ink.muted)
            Copy("DATABASE",11,Ink.muted)
            SelectionContainer {Copy(settings.database.toAbsolutePath().normalize().toString(),13,modifier=Modifier.testTag("storage-path"))}
            Copy("APPEARANCE PREFERENCES",11,Ink.muted)
            SelectionContainer {Copy(settings.preferenceFile.toAbsolutePath().normalize().toString(),13)}
            Copy("Storage is configured at launch with PROFESSOR_OS_DATA_DIR; the default is the data folder. To use another location, close the application, back up your data and set that variable before restarting. This screen does not move or recreate your database.",13,Ink.muted)
        }
        Panel(Modifier.fillMaxWidth()) {
            Copy("Academic organization",20)
            Copy("Manage fall, spring and summer sessions from Terms. Courses and offerings remain available in the sidebar.",13,Ink.muted)
            Action("Manage terms","calendar",modifier=Modifier.testTag("settings-terms"),onClick=openTerms)
        }
    }
}
