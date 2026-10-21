package com.professoros.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.professoros.model.DashboardOverview
import java.time.format.DateTimeFormatter

object Ink {
    val background = Color(0xFF080D1B)
    val panel = Color(0xFF121B30)
    val raised = Color(0xFF1B2440)
    val border = Color(0xFF303D59)
    val text = Color(0xFFF0F3FC)
    val muted = Color(0xFFA8B5CE)
    val violet = Color(0xFFA991FF)
    val cyan = Color(0xFF7DE0EA)
}
// The variable font's default axis is ExtraLight; explicitly select each intended weight.
private val manrope = FontFamily(listOf(400, 600, 700).map { weight ->
    Font("fonts/Manrope.ttf", FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})
private val arabic = FontFamily(Font("fonts/IBMPlexSansArabic-Regular.ttf"), Font("fonts/IBMPlexSansArabic-SemiBold.ttf", FontWeight.SemiBold))
private val ArabicText = Regex("[\\u0600-\\u06FF]")
@Composable fun Copy(text: String, size: Int = 14, color: Color = Ink.text, weight: FontWeight = FontWeight.Normal,
                     modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size.sp, lineHeight = (size * 1.5).sp,
        fontFamily = if (ArabicText.containsMatchIn(text)) arabic else manrope, fontWeight = weight,
        textDirection = TextDirection.Content), maxLines = lines, overflow = TextOverflow.Ellipsis)
}
@Composable private fun Eyebrow(text: String) = Copy(text, 11, Ink.cyan, FontWeight.SemiBold)
@Composable private fun Action(label: String, icon: String = "arrow", active: Boolean = false, reduced: Boolean = false,
                               modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(if (active) Color(0xFF382968) else if (hovered) Ink.raised else Color.Transparent,
        tween(if (reduced) 0 else 140))
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(fill)
        .border(1.dp, if (focused) Ink.cyan else if (active) Ink.violet.copy(alpha = .4f) else Ink.border.copy(alpha = .4f), RoundedCornerShape(12.dp))
        .hoverable(interaction).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mark(icon, if (active) Ink.violet else Ink.muted)
        Copy(label, 13, if (active) Ink.text else Ink.muted, FontWeight.SemiBold)
    }
}
/** Small original geometric icons, drawn locally; no icon pack or external assets. */
@Composable private fun Mark(kind: String, color: Color = Ink.violet, size: Int = 20) {
    Canvas(Modifier.size(size.dp).semantics { contentDescription = kind }) {
        val u = this.size.width / 20f
        fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(color, Offset(a*u,b*u), Offset(c*u,d*u), 1.6f*u, StrokeCap.Round)
        when (kind) {
            "grid" -> for (x in listOf(2f, 11f)) for (y in listOf(2f,11f)) drawRoundRect(color, Offset(x*u,y*u), androidx.compose.ui.geometry.Size(6*u,6*u), androidx.compose.ui.geometry.CornerRadius(1.5f*u), style=Stroke(1.4f*u))
            "book" -> { line(3f,3f,9f,5f);line(9f,5f,17f,3f);line(3f,3f,3f,16f);line(17f,3f,17f,16f);line(3f,16f,9f,18f);line(9f,18f,17f,16f);line(9f,5f,9f,18f) }
            "pulse" -> { line(1f,11f,5f,11f);line(5f,11f,8f,3f);line(8f,3f,12f,17f);line(12f,17f,15f,9f);line(15f,9f,19f,9f) }
            "people" -> { drawCircle(color,3*u,Offset(10*u,5*u),style=Stroke(1.4f*u));drawArc(color,180f,180f,false,Offset(3*u,10*u),androidx.compose.ui.geometry.Size(14*u,13*u),style=Stroke(1.4f*u)) }
            "clock" -> { drawCircle(color,8*u,Offset(10*u,10*u),style=Stroke(1.4f*u));line(10f,5f,10f,10f);line(10f,10f,14f,12f) }
            "motion" -> { line(2f,5f,14f,5f);line(5f,10f,18f,10f);line(2f,15f,11f,15f) }
            else -> { line(3f,10f,17f,10f);line(12f,5f,17f,10f);line(12f,15f,17f,10f) }
        }
    }
}
@Composable private fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.shadow(16.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(0xF01A2440), Color(0xF010182B))))
        .border(1.dp, Brush.linearGradient(listOf(Color(0xFF3E4A69), Color(0xFF202B43))), RoundedCornerShape(22.dp))
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun ProfessorDashboard(snapshot: DashboardSnapshot?, loading: Boolean, error: String?, page: String,
    navigate: (String) -> Unit, reduced: Boolean, motion: MotionMode, setMotion: (MotionMode) -> Unit,
    refresh: () -> Unit, selectOffering: (Long) -> Unit, openWorkspace: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.background).testTag("dashboard-root")) {
        val compact = maxWidth < 1100.dp
        val narrow = maxWidth < 1000.dp
        Ambient(reduced)
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(if (compact) 86.dp else 228.dp).fillMaxHeight().background(Color(0xC00B1222))
                .border(1.dp, Ink.border.copy(alpha=.4f)).padding(if (compact) 12.dp else 22.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.padding(vertical=20.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF8656F0),Color(0xFF4442AC)))),contentAlignment=Alignment.Center){Copy("P",25,weight=FontWeight.Bold)}
                    if (!compact) Column { Copy("Professor OS",17,weight=FontWeight.Bold); Copy("TEACHING, REIMAGINED",9,Ink.muted) }
                }
                if (!compact) Eyebrow("YOUR WORKSPACE")
                for ((label,icon) in listOf("Overview" to "grid", "Teaching context" to "book", "Activity" to "pulse")) {
                    if(compact) Box(Modifier.semantics { contentDescription=label }) { Action("",icon,page==label,reduced,Modifier.testTag("nav-$label")){navigate(label)} }
                    else Action(label,icon,page==label,reduced,Modifier.fillMaxWidth().testTag("nav-$label")){navigate(label)}
                }
                Spacer(Modifier.weight(1f))
                if (!compact) {
                    Box(Modifier.size(7.dp).background(Ink.cyan,RoundedCornerShape(4.dp)))
                    Copy("Your work. Your device.",13,weight=FontWeight.SemiBold)
                    Copy("An offline workspace for a more intentional academic day.",12,Ink.muted)
                }
                Action(if(compact) "" else "Open JavaFX", "arrow",reduced=reduced,modifier=Modifier.testTag("open-javafx").semantics { contentDescription = "Open JavaFX workspace" },onClick=openWorkspace)
                if (!compact) Copy("Full workspace · existing app",10,Ink.muted)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(if(narrow) 22.dp else 36.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column { Eyebrow("WORKSPACE / ${page.uppercase()}"); Copy("${snapshot?.overview?.asOf()?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) ?: "Professor OS"}",13,Ink.muted) }
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Action("Motion: ${motion.name.lowercase()}","motion",reduced=reduced,modifier=Modifier.testTag("motion")){setMotion(MotionMode.entries[(motion.ordinal+1)%3])}
                        Action(if(loading) "Loading…" else "Refresh","pulse",reduced=reduced,modifier=Modifier.testTag("refresh"),onClick=refresh)
                    }
                }
                error?.let { Glass(Modifier.fillMaxWidth()) {Copy("Workspace unavailable",20,weight=FontWeight.Bold);Copy(it,14,Ink.muted);Action("Try again",reduced=reduced,onClick=refresh)} }
                if(snapshot==null) { if(error==null) Copy("Opening your saved workspace…",24); return@Column }
                Crossfade(page,animationSpec=tween(if(reduced)0 else 220),label="navigation") { destination ->
                    Column(verticalArrangement=Arrangement.spacedBy(22.dp)) {
                        when(destination) {
                            "Teaching context" -> ContextPicker(snapshot,reduced,selectOffering)
                            "Activity" -> { Copy("The shape of your week",32,weight=FontWeight.Bold); ActivityPanel("UP NEXT","Upcoming teaching",snapshot.overview.upcoming());ActivityPanel("RECENTLY UPDATED","Your latest work",snapshot.overview.recentlyUpdated()) }
                            else -> {
                                Hero(snapshot,reduced,narrow){navigate("Teaching context")}
                                Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                    Metric("Courses",snapshot.courses,"Reusable catalog","book",reduced,Modifier.weight(1f))
                                    Metric("Students",snapshot.overview.students(),"Across your workspace","people",reduced,Modifier.weight(1f))
                                    Metric("Offerings",snapshot.overview.offerings(),"All terms & sections","grid",reduced,Modifier.weight(1f))
                                }
                                if(narrow) { ProgressPanel(snapshot,reduced,Modifier.fillMaxWidth());ActivityPanel("UP NEXT","Upcoming teaching",snapshot.overview.upcoming()) }
                                else Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                                    ProgressPanel(snapshot,reduced,Modifier.weight(.95f));ActivityPanel("UP NEXT","Upcoming teaching",snapshot.overview.upcoming(),Modifier.weight(1.4f))
                                }
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Copy("LIVE FROM YOUR LOCAL WORKSPACE",10,Ink.muted)
                                    Copy("Compose evaluation · read-only dashboard",10,Ink.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun Ambient(reduced: Boolean) {
    val alpha = if(reduced) remember { mutableStateOf(.12f) } else {
        val transition=rememberInfiniteTransition(label="ambient")
        transition.animateFloat(.10f,.18f,infiniteRepeatable(tween(8000),RepeatMode.Reverse),label="ambient-opacity")
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha=alpha.value }) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF714BDA),Color.Transparent),Offset(size.width*.72f,80f),size.width*.5f),size.width*.5f,Offset(size.width*.72f,80f))
    }
}
@Composable private fun Hero(snapshot: DashboardSnapshot,reduced: Boolean,narrow: Boolean,choose:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF2B2257),Color(0xFF18253E),Color(0xFF12272E))))
        .border(1.dp,Ink.violet.copy(alpha=.35f),RoundedCornerShape(26.dp)).padding(if(narrow)26.dp else 32.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Eyebrow("A LITTLE CLARITY. A LOT OF POSSIBILITY.")
            Copy("Make room for\nyour best teaching.",if(narrow)34 else 42,weight=FontWeight.Bold)
            Copy("مرحباً بك في مساحتك الأكاديمية",20,Ink.cyan,modifier=Modifier.testTag("arabic-welcome"))
            Copy(snapshot.selected?.label()?:"Choose a teaching context to see lecture progress and upcoming work.",13,Ink.muted)
            Spacer(Modifier.height(4.dp));Action("${if(snapshot.selected==null)"Choose" else "Change"} teaching context","arrow",true,reduced,Modifier.testTag("choose-context"),choose)
        }
        if(!narrow) Orbit(snapshot.overview.progress()?.completionPercent()?.toFloat(),reduced)
    }
}
@Composable private fun Orbit(progress: Float?,reduced: Boolean) {
    val sweep by animateFloatAsState(progress?:0f,tween(if(reduced)0 else 900),label="completion-ring")
    Box(Modifier.size(210.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Ink.violet.copy(alpha=.16f),Color.Transparent)),size.width*.5f)
            drawCircle(Ink.border,size.width*.40f,style=Stroke(1f))
            drawArc(Ink.border,-90f,360f,false,Offset(23f,23f),androidx.compose.ui.geometry.Size(size.width-46f,size.height-46f),style=Stroke(7f))
            drawArc(Brush.sweepGradient(listOf(Ink.violet,Ink.cyan,Ink.violet)),-90f,sweep*3.6f,false,Offset(23f,23f),androidx.compose.ui.geometry.Size(size.width-46f,size.height-46f),style=Stroke(7f,cap=StrokeCap.Round))
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) {Copy(if(progress==null)"—" else "${sweep.toInt()}%",44,weight=FontWeight.Bold);Copy("LECTURES COMPLETED",9,Ink.muted)}
    }
}
@Composable private fun Metric(title:String,count:Int,detail:String,icon:String,reduced:Boolean,modifier:Modifier) {
    val value by animateIntAsState(count,tween(if(reduced)0 else 650),label="counter-$title")
    Glass(modifier) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Copy(title,13,Ink.muted);Mark(icon,Ink.violet)};Copy(value.toString(),36,weight=FontWeight.Bold);Copy(detail,11,Ink.muted)}
}
@Composable private fun ProgressPanel(snapshot:DashboardSnapshot,reduced:Boolean,modifier:Modifier) {
    val progress=snapshot.overview.progress()
    Glass(modifier) {
        Eyebrow("TEACHING PULSE");Copy("Steady progress.",25,weight=FontWeight.Bold)
        Copy(snapshot.selected?.courseName()?:"No teaching context",14,Ink.muted,lines=2)
        val fraction by animateFloatAsState(((progress?.completionPercent()?:0.0)/100).toFloat(),tween(if(reduced)0 else 650),label="progress")
        Box(Modifier.fillMaxWidth().height(6.dp).background(Ink.border,RoundedCornerShape(3.dp))) {Box(Modifier.fillMaxWidth(fraction.coerceIn(0f,1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(Ink.violet,Ink.cyan)),RoundedCornerShape(3.dp)))}
        Copy(if(progress==null)"Select an offering to see saved lecture progress." else "${progress.completed()} of ${progress.scheduledTotal()} lectures completed",14)
        Copy(progress?.scheduleStatus()?:"No scheduled lectures",12,Ink.muted)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.border))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Copy("Active enrollments",13,Ink.muted);Copy(snapshot.overview.activeEnrollments()?.toString()?:"—",17,weight=FontWeight.SemiBold)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Copy("Recorded attendance",13,Ink.muted);Copy(snapshot.attendance?.percentage()?.let{"$it%"}?:"Not yet rated",17,Ink.cyan,FontWeight.SemiBold)}
        Copy("Attendance excludes excused and unmarked. Late counts fully.",10,Ink.muted)
    }
}
@Composable private fun ActivityPanel(eyebrow:String,title:String,activities:List<DashboardOverview.Activity>,modifier:Modifier=Modifier) {
    Glass(modifier.fillMaxWidth()) {
        Eyebrow(eyebrow);Copy(title,25,weight=FontWeight.Bold)
        if(activities.isEmpty()) Copy("No saved activities to show for this teaching context.",14,Ink.muted)
        for(activity in activities) Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.width(48.dp).background(Ink.raised,RoundedCornerShape(12.dp)).padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally){Copy(activity.date().dayOfMonth.toString(),21,weight=FontWeight.Bold);Copy(activity.date().format(DateTimeFormatter.ofPattern("MMM")).uppercase(),9,Ink.violet)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Copy(activity.title(),15,weight=FontWeight.SemiBold,lines=2);Copy("${activity.kind()} · ${activity.detail()}",11,Ink.muted,lines=2)}
            Mark(if(activity.kind()=="Lecture")"book" else "clock",Ink.muted)
        }
    }
}
@Composable private fun ContextPicker(snapshot:DashboardSnapshot,reduced:Boolean,select:(Long)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Eyebrow("FOCUS YOUR WORKSPACE");Copy("Every course. A new perspective.",32,weight=FontWeight.Bold)
        Copy("Choose a saved offering to focus the overview. Manage records in the JavaFX workspace.",14,Ink.muted)
        if(snapshot.offerings.isEmpty()) Glass {Copy("No offerings yet",22);Copy("Create a course and an offering in the full workspace, then refresh.",14,Ink.muted)}
        for(offering in snapshot.offerings) Glass(Modifier.fillMaxWidth()) {
            Copy(offering.courseName(),24,weight=FontWeight.Bold);Copy(offering.label(),14,Ink.muted)
            Action("Open overview","arrow",snapshot.selected?.offering()?.id()==offering.offering().id(),reduced,Modifier.testTag("offering-${offering.offering().id()}")){select(offering.offering().id())}
        }
    }
}
