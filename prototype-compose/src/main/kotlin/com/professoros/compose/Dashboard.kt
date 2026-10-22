package com.professoros.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.time.format.DateTimeFormatter

private val destinations = listOf("Overview" to "grid", "Teaching context" to "book", "Activity" to "pulse")
private val workspaceDestinations = listOf("Overview" to "grid", "Courses" to "book", "Students" to "people", "Terms" to "clock",
    "Offerings" to "book", "Teaching context" to "people", "Lectures" to "book", "Activity" to "pulse")

@Composable fun ProfessorDashboard(snapshot: DashboardSnapshot?, loading: Boolean, error: String?, page: String,
    navigate: (String) -> Unit, reduced: Boolean, motion: MotionMode, setMotion: (MotionMode) -> Unit,
    refresh: () -> Unit, selectOffering: (Long) -> Unit, openWorkspace: () -> Unit,
    featureContent: (@Composable (String) -> Unit)? = null) {
    var picker by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.background).testTag("dashboard-root")
        .onPreviewKeyEvent {
            if(it.type != KeyEventType.KeyDown) false
            else if((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.K) { picker=!picker; true }
            else if((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.R && !loading) { refresh(); true }
            else false
        }) {
        val compact = maxWidth < 1050.dp
        val narrow = maxWidth < 1120.dp
        Row(Modifier.fillMaxSize()) {
            Sidebar(page,compact,reduced,navigate,openWorkspace,featureContent!=null)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(64.dp).background(Ink.sidebar.copy(alpha=.75f))
                    .padding(horizontal=24.dp), verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Copy(if(compact) page else "Workspace  /  " + page,12,Ink.muted,modifier=Modifier.weight(1f),lines=1)
                    Box {
                        Action(snapshot?.selected?.let { it.courseCode()+" · "+it.termName()+" · "+it.offering().section() } ?: "Choose offering","chevrons",reduced=reduced,
                            modifier=Modifier.widthIn(max=280.dp).testTag("choose-context").semantics { contentDescription="Choose teaching context, Control K" }) { picker=true }
                        if(picker) Popup(alignment=Alignment.TopEnd,offset=IntOffset(0,46),onDismissRequest={picker=false},
                            properties=PopupProperties(focusable=true)) {
                            Panel(Modifier.width(400.dp).heightIn(max=460.dp).testTag("context-popup")
                                .onPreviewKeyEvent {
                                    if(it.type==KeyEventType.KeyDown && it.key==Key.Escape) {picker=false;true} else false
                                }) {
                                Copy("Switch teaching context",15,weight=FontWeight.SemiBold)
                                OfferingPicker(snapshot,reduced,dismissible=true) { picker=false;selectOffering(it) }
                            }
                        }
                    }
                    Action(if(compact) "" else motion.name.lowercase().replaceFirstChar { it.uppercase() },"motion",reduced=reduced,
                        modifier=Modifier.testTag("motion").semantics { contentDescription="Motion: "+motion.name.lowercase() }) {
                        setMotion(MotionMode.entries[(motion.ordinal+1)%3])
                    }
                    Action("","refresh",reduced=reduced,enabled=!loading,
                        modifier=Modifier.testTag("refresh").semantics { contentDescription=if(loading)"Refreshing workspace" else "Refresh workspace, Control R" },onClick=refresh)
                }
                Rule()
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),contentAlignment=Alignment.TopCenter) {
                    Column(Modifier.widthIn(max=1340.dp).fillMaxWidth().padding(if(compact)24.dp else 34.dp),
                        verticalArrangement=Arrangement.spacedBy(24.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Copy(snapshot?.overview?.asOf()?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) ?: "Local workspace",12,Ink.muted)
                                Copy(when(page) {"Teaching context" -> "Your teaching context";"Activity" -> "Your academic week";"Overview" -> "Your workspace";else -> page},28,weight=FontWeight.SemiBold)
                            }
                            if(!compact) Copy("مساحتك الأكاديمية",16,Ink.muted,modifier=Modifier.testTag("arabic-welcome"))
                        }
                        error?.let { Panel(Modifier.fillMaxWidth()) {
                            Copy("Workspace unavailable",16,weight=FontWeight.SemiBold);Copy(it,13,Ink.muted)
                            Action("Try again","refresh",reduced=reduced,onClick=refresh)
                        } }
                        if(snapshot==null) {
                            if(error==null) Copy("Opening your saved workspace…",14,Ink.muted)
                        } else {
                            AnimatedContent(page,transitionSpec={
                                (fadeIn(tween(if(reduced)0 else 170)) + slideInVertically(motion(reduced)) { if(reduced)0 else 12 })
                                    .togetherWith(fadeOut(tween(if(reduced)0 else 80)))
                                    .using(SizeTransform(clip=false,sizeAnimationSpec={_,_->motion(reduced)}))
                            },label="page-reveal") { destination ->
                                if(featureContent!=null && destination !in listOf("Overview","Activity")) featureContent(destination)
                                else when(destination) {
                                    "Teaching context" -> Panel(Modifier.fillMaxWidth()) {
                                        Copy("Select an offering",16,weight=FontWeight.SemiBold)
                                        Copy("A course, term and section. Your overview follows the selected context.",13,Ink.muted)
                                        OfferingPicker(snapshot,reduced,select=selectOffering)
                                    }
                                    "Activity" -> Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                        ActivityPanel("Coming up",snapshot.overview.upcoming(),"No upcoming teaching",
                                            "Planned lectures and saved exams will appear here.")
                                        ActivityPanel("Recently updated",snapshot.overview.recentlyUpdated(),"A quiet workspace",
                                            "Updates to lectures and exams appear here as you work.",recent=true)
                                    }
                                    else -> Overview(snapshot,reduced,narrow,openWorkspace)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Copy("Saved locally · available offline",11,Ink.muted)
                            Copy(if(featureContent==null)"Dashboard preview" else "JavaFX fallback available",11,Ink.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Sidebar(page:String,compact:Boolean,reduced:Boolean,navigate:(String)->Unit,open:()->Unit,features:Boolean=false) {
    val destinations=if(features)workspaceDestinations else destinations
    Column(Modifier.width(if(compact)68.dp else 210.dp).fillMaxHeight().background(Ink.sidebar)
        .padding(horizontal=if(compact)12.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Box(Modifier.height(76.dp),contentAlignment=Alignment.CenterStart){Wordmark(compact)}
        if(!compact) Copy("WORKSPACE",10,Ink.muted,FontWeight.Medium,Modifier.padding(start=10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            val offset by animateDpAsState((destinations.indexOfFirst{it.first==page}.coerceAtLeast(0)*44).dp,
                motion(reduced),label="navigation-indicator")
            Box(Modifier.offset(y=offset).fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp))
                .background(Ink.selected).testTag("navigation-indicator"))
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                destinations.forEach { (label,icon) ->
                    Action(if(compact)"" else label,icon,page==label,reduced,
                        Modifier.fillMaxWidth().height(38.dp).testTag("nav-"+label).semantics {
                            contentDescription=label;selected=page==label
                        },quiet=true) {navigate(label)}
                }
            }
        }
        if(!compact) {
            Rule()
            Copy("One place for your\nacademic work.",12,Ink.muted,modifier=Modifier.padding(horizontal=10.dp))
        }
        Action(if(compact)"" else "Open full workspace","external",reduced=reduced,
            modifier=Modifier.fillMaxWidth().testTag("open-javafx").semantics {contentDescription="Open JavaFX workspace"},
            quiet=true,onClick=open)
        if(!compact) Row(Modifier.padding(start=10.dp,bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(Ink.positive,RoundedCornerShape(3.dp)));Copy("On your device",11,Ink.muted)
        } else Spacer(Modifier.height(4.dp))
    }
}
