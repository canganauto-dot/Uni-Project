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

private val destinations = listOf("Overview" to "grid", "Teaching context" to "book", "Activity" to "pulse", "Settings" to "settings")
private val workspaceDestinations = listOf("Overview" to "grid", "Courses" to "book", "Students" to "people", "Terms" to "calendar",
    "Offerings" to "layers", "Teaching context" to "chevrons", "Lectures" to "lecture", "Question Bank" to "question", "Exam Builder" to "exam", "Gradebook" to "grid", "Attendance" to "check", "Activity" to "pulse", "Settings" to "settings")

@Composable fun ProfessorDashboard(snapshot: DashboardSnapshot?, loading: Boolean, error: String?, page: String,
    navigate: (String) -> Unit,
    refresh: () -> Unit, selectOffering: (Long) -> Unit,
    featureContent: (@Composable (String) -> Unit)? = null) {
    var picker by remember { mutableStateOf(false) }
    val pageScroll=rememberScrollState()
    LaunchedEffect(page) { pageScroll.scrollTo(0) }
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
            Sidebar(page,compact,navigate,featureContent!=null)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(64.dp).background(Ink.sidebar.copy(alpha=.75f))
                    .padding(horizontal=24.dp), verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Copy(if(compact) page else "Workspace  /  " + page,12,Ink.muted,modifier=Modifier.weight(1f),lines=1)
                    Box {
                        Action(snapshot?.selected?.let { it.courseCode()+" · "+it.termName()+" · "+it.offering().section() } ?: "Choose offering","chevrons",
                            modifier=Modifier.widthIn(max=280.dp).testTag("choose-context").semantics { contentDescription="Choose teaching context, Control K" }) { picker=true }
                        if(picker) Popup(alignment=Alignment.TopEnd,offset=IntOffset(0,46),onDismissRequest={picker=false},
                            properties=PopupProperties(focusable=true)) {
                            Panel(Modifier.width(400.dp).heightIn(max=460.dp).testTag("context-popup")
                                .onPreviewKeyEvent {
                                    if(it.type==KeyEventType.KeyDown && it.key==Key.Escape) {picker=false;true} else false
                                }) {
                                Copy("Switch teaching context",15,weight=FontWeight.SemiBold)
                                OfferingPicker(snapshot,dismissible=true) { picker=false;selectOffering(it) }
                            }
                        }
                    }
                    Action("","refresh",enabled=!loading,
                        modifier=Modifier.testTag("refresh").semantics { contentDescription=if(loading)"Refreshing workspace" else "Refresh workspace, Control R" },onClick=refresh)
                }
                Rule()
                Box(Modifier.fillMaxSize().verticalScroll(pageScroll).testTag("page-scroll"),contentAlignment=Alignment.TopCenter) {
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
                            Action("Try again","refresh",onClick=refresh)
                        } }
                        if(page=="Settings" && featureContent!=null) {
                            featureContent(page)
                        } else if(snapshot==null) {
                            if(error==null) Copy("Opening your saved workspace…",14,Ink.muted)
                        } else {
                            AnimatedContent(page,transitionSpec={
                                (fadeIn(tween(170)) + slideInVertically(motion()) { 12 })
                                    .togetherWith(fadeOut(tween(80)))
                                    .using(SizeTransform(clip=false,sizeAnimationSpec={_,_->motion()}))
                            },label="page-reveal") { destination ->
                                if(featureContent!=null && destination !in listOf("Overview","Activity")) featureContent(destination)
                                else when(destination) {
                                    "Teaching context" -> Panel(Modifier.fillMaxWidth()) {
                                        Copy("Select an offering",16,weight=FontWeight.SemiBold)
                                        Copy("A course, term and section. Your overview follows the selected context.",13,Ink.muted)
                                        OfferingPicker(snapshot,select=selectOffering)
                                    }
                                    "Activity" -> Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                        ActivityPanel("Coming up",snapshot.overview.upcoming(),"No upcoming teaching",
                                            "Planned lectures and saved exams will appear here.")
                                        ActivityPanel("Recently updated",snapshot.overview.recentlyUpdated(),"A quiet workspace",
                                            "Updates to lectures and exams appear here as you work.",recent=true)
                                    }
                                    else -> Overview(snapshot,narrow) { navigate(if(snapshot.selected==null) "Offerings" else "Lectures") }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Copy("Saved locally · available offline",11,Ink.muted)
                            Copy("Professor OS",11,Ink.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Sidebar(page:String,compact:Boolean,navigate:(String)->Unit,features:Boolean=false) {
    val destinations=if(features)workspaceDestinations else destinations
    Column(Modifier.width(if(compact)68.dp else 210.dp).fillMaxHeight().background(Ink.sidebar)
        .padding(horizontal=if(compact)12.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Box(Modifier.height(76.dp),contentAlignment=Alignment.CenterStart){Wordmark(compact)}
        if(!compact) Copy("WORKSPACE",10,Ink.muted,FontWeight.Medium,Modifier.padding(start=10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            val offset by animateDpAsState((destinations.indexOfFirst{it.first==page}.coerceAtLeast(0)*44).dp,
                motion(),label="navigation-indicator")
            Box(Modifier.offset { IntOffset(0,offset.roundToPx()) }.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp))
                .background(Ink.selected).testTag("navigation-indicator"))
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                destinations.forEach { (label,icon) ->
                    Action(if(compact)"" else label,icon,page==label,
                        Modifier.fillMaxWidth().height(38.dp).testTag("nav-"+label).semantics {
                            contentDescription=label;selected=page==label
                        },quiet=true,hint=if(compact)label else null) {navigate(label)}
                }
            }
        }
        if(!compact) {
            Rule()
            Copy("One place for your\nacademic work.",12,Ink.muted,modifier=Modifier.padding(horizontal=10.dp))
        }
        if(!compact) Row(Modifier.padding(start=10.dp,bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(Ink.positive,RoundedCornerShape(3.dp)));Copy("On your device",11,Ink.muted)
        } else Spacer(Modifier.height(4.dp))
    }
}
