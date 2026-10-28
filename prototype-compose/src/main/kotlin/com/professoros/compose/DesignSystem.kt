package com.professoros.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup

/** Shared presentation tokens. No Material components or platform-specific rendering dependencies. */
data class Palette(val background: Color, val sidebar: Color, val panel: Color, val raised: Color, val selected: Color, val border: Color, val text: Color, val muted: Color, val violet: Color, val positive: Color, val accentSurface: Color)
val DarkPalette = Palette(Color(0xFF141824),Color(0xFF181D2B),Color(0xFF202735),Color(0xFF293243),Color(0xFF38354C),Color(0xFF495368),Color(0xFFEDEFF6),Color(0xFFB6BFD0),Color(0xFFC7B7E7),Color(0xFFA4CFBE),Color(0xFF2D293E))
val LightPalette = Palette(Color(0xFFF5F4F7),Color(0xFFEFEDF2),Color(0xFFFFFFFF),Color(0xFFF0EEF4),Color(0xFFE4DFED),Color(0xFFA4A6B4),Color(0xFF202027),Color(0xFF57515F),Color(0xFF644A83),Color(0xFF35634D),Color(0xFFEDE7F4))
internal val LocalPalette = staticCompositionLocalOf { DarkPalette }
object Ink {
    val background: Color @Composable get() = LocalPalette.current.background
    val sidebar: Color @Composable get() = LocalPalette.current.sidebar
    val panel: Color @Composable get() = LocalPalette.current.panel
    val raised: Color @Composable get() = LocalPalette.current.raised
    val selected: Color @Composable get() = LocalPalette.current.selected
    val border: Color @Composable get() = LocalPalette.current.border
    val text: Color @Composable get() = LocalPalette.current.text
    val muted: Color @Composable get() = LocalPalette.current.muted
    val violet: Color @Composable get() = LocalPalette.current.violet
    val positive: Color @Composable get() = LocalPalette.current.positive
    val accentSurface: Color @Composable get() = LocalPalette.current.accentSurface
    val radius = 14.dp
}
internal val LocalControlsEnabled = staticCompositionLocalOf { true }
internal val manrope = FontFamily(listOf(400, 500, 600, 700).map { weight ->
    Font("fonts/Manrope.ttf", FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})
private val arabic = FontFamily(Font("fonts/IBMPlexSansArabic-Regular.ttf"),
    Font("fonts/IBMPlexSansArabic-SemiBold.ttf", FontWeight.SemiBold))
private val ArabicText = Regex("[\\u0600-\\u06FF]")
internal fun fontFor(text: String) = if (ArabicText.containsMatchIn(text)) arabic else manrope
@Composable fun Copy(text: String, size: Int = 14, color: Color = Ink.text, weight: FontWeight = FontWeight.Normal,
                     modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size.sp, lineHeight = (size * 1.45).sp,
        fontFamily = fontFor(text), fontWeight = weight,
        textDirection = TextDirection.Content), maxLines = lines, overflow = TextOverflow.Ellipsis)
}
internal fun <T> motion(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 420f)

@Composable internal fun Rule() = Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.border.copy(alpha=.65f)))

/** Intent is explicit at call sites; labels never determine action semantics. */
internal enum class ActionVariant { PRIMARY, SUCCESS, DESTRUCTIVE, SECONDARY, WARNING }
internal data class ActionTone(val surface:Color,val foreground:Color,val border:Color)
internal fun actionTone(variant:ActionVariant,light:Boolean=false):ActionTone {
    fun tone(surface:Long,foreground:Long,border:Long)=ActionTone(Color(surface),Color(foreground),Color(border))
    if(light)return when(variant) {
        ActionVariant.PRIMARY -> tone(0xFF5D4C80,0xFFFFFFFF,0xFF786595)
        ActionVariant.SUCCESS -> tone(0xFFE6EFEB,0xFF285744,0xFF9AB9AA)
        ActionVariant.DESTRUCTIVE -> tone(0xFFF3E7EB,0xFF883E50,0xFFC69AA6)
        ActionVariant.SECONDARY -> tone(0xFFEBECF2,0xFF3E495E,0xFFA4A9B8)
        ActionVariant.WARNING -> tone(0xFFF2EDE3,0xFF735521,0xFFC2AE86)
    }
    return when(variant) {
        ActionVariant.PRIMARY -> tone(0xFF51436F,0xFFF1EBFF,0xFF71608F)
        ActionVariant.SUCCESS -> tone(0xFF243532,0xFF95D2B5,0xFF426052)
        ActionVariant.DESTRUCTIVE -> tone(0xFF382A32,0xFFE3A9AE,0xFF6C4851)
        ActionVariant.SECONDARY -> tone(0xFF252D3C,0xFFCAD3E3,0xFF465167)
        ActionVariant.WARNING -> tone(0xFF363128,0xFFDFC08B,0xFF685B43)
    }
}

@Composable internal fun Action(label: String, icon: String = "arrow", active: Boolean = false,
    modifier: Modifier = Modifier, quiet: Boolean = false,
    enabled: Boolean = true, hint: String? = null, variant: ActionVariant = ActionVariant.SECONDARY, onClick: () -> Unit) {
    val enabled = enabled && LocalControlsEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val tone=actionTone(if(active)ActionVariant.PRIMARY else variant,LocalPalette.current.background.luminance()>.5f)
    val surface=tone.surface
    val foreground=if(!enabled)Ink.muted else if(quiet&&!active&&!hovered&&!pressed)Ink.text else tone.foreground
    val fill by animateColorAsState(
        if(!enabled) Ink.raised else if(pressed) lerp(surface,Color.Black,.08f)
        else if(hovered) lerp(surface,Color.White,.04f) else if(quiet&&!active) Color.Transparent else surface,
        motion(), label = "button-surface")
    Box {
    Row(modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp)).background(fill)
        .border(if(focused)2.dp else 1.dp, if (focused) Ink.violet else if (quiet&&!active) Color.Transparent else if(!enabled)Ink.border.copy(alpha=.5f) else tone.border, RoundedCornerShape(8.dp))
        .hoverable(interaction).clickable(interactionSource = interaction, indication = null, enabled = enabled,
            role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Mark(icon, foreground, 16)
        if (label.isNotEmpty()) Copy(label, 12, foreground, FontWeight.Medium, lines = 1)
    }
    if(hint!=null && (hovered||focused)) Popup(alignment=Alignment.TopStart,offset=IntOffset(52,0)) {
        Box(Modifier.background(Ink.raised,RoundedCornerShape(6.dp)).border(1.dp,Ink.border,RoundedCornerShape(6.dp)).padding(8.dp)){Copy(hint,12)}
    }
    }
}

@Composable internal fun Panel(modifier: Modifier = Modifier, tinted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(Ink.radius))
        .background(if (tinted) Ink.accentSurface else Ink.panel)
        .border(1.dp, Ink.border, RoundedCornerShape(Ink.radius))
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/** Original, consistent 16/20px line icons; decorative icons are excluded from accessibility text. */
@Composable internal fun Mark(kind: String, color: Color = Ink.muted, size: Int = 18) {
    Canvas(Modifier.size(size.dp)) {
        val u = this.size.width / 20f
        fun line(a: Float,b: Float,c: Float,d: Float) = drawLine(color,Offset(a*u,b*u),Offset(c*u,d*u),1.4f*u,StrokeCap.Round)
        when(kind) {
            "calendar" -> {drawRect(color,Offset(3*u,5*u),androidx.compose.ui.geometry.Size(14*u,12*u),style=Stroke(1.3f*u));line(3f,9f,17f,9f);line(6f,3f,6f,7f);line(14f,3f,14f,7f)}
            "layers" -> {line(3f,6f,10f,2f);line(10f,2f,17f,6f);line(17f,6f,10f,10f);line(10f,10f,3f,6f);line(3f,11f,10f,15f);line(10f,15f,17f,11f);line(3f,15f,10f,19f);line(10f,19f,17f,15f)}
            "lecture" -> {drawRect(color,Offset(3*u,3*u),androidx.compose.ui.geometry.Size(14*u,10*u),style=Stroke(1.3f*u));line(10f,13f,10f,17f);line(6f,17f,14f,17f);line(6f,6f,14f,6f);line(6f,9f,12f,9f)}
            "question" -> {drawCircle(color,7.5f*u,Offset(10*u,10*u),style=Stroke(1.3f*u));drawArc(color,180f,250f,false,Offset(7*u,5*u),androidx.compose.ui.geometry.Size(6*u,6*u),style=Stroke(1.3f*u));line(10f,10f,10f,12f);drawCircle(color,.8f*u,Offset(10*u,15*u))}
            "exam" -> {drawRect(color,Offset(4*u,2*u),androidx.compose.ui.geometry.Size(12*u,16*u),style=Stroke(1.3f*u));line(7f,6f,13f,6f);line(7f,10f,13f,10f);line(7f,14f,11f,14f)}
            "grid" -> for(x in listOf(3f,11f)) for(y in listOf(3f,11f))
                drawRoundRect(color,Offset(x*u,y*u),androidx.compose.ui.geometry.Size(6*u,6*u),
                    androidx.compose.ui.geometry.CornerRadius(1.2f*u),style=Stroke(1.3f*u))
            "book" -> {line(3f,4f,9f,5f);line(9f,5f,17f,4f);line(3f,4f,3f,16f);line(17f,4f,17f,16f);line(3f,16f,9f,17f);line(9f,17f,17f,16f);line(9f,5f,9f,17f)}
            "pulse" -> {line(2f,12f,6f,12f);line(6f,12f,9f,5f);line(9f,5f,12f,16f);line(12f,16f,15f,9f);line(15f,9f,18f,9f)}
            "people" -> {drawCircle(color,3*u,Offset(10*u,6*u),style=Stroke(1.4f*u));drawArc(color,180f,180f,false,Offset(4*u,11*u),androidx.compose.ui.geometry.Size(12*u,11*u),style=Stroke(1.4f*u))}
            "clock" -> {drawCircle(color,7*u,Offset(10*u,10*u),style=Stroke(1.4f*u));line(10f,6f,10f,10f);line(10f,10f,13f,12f)}
            "check" -> {line(4f,10f,8f,14f);line(8f,14f,16f,6f)}
            "chevrons" -> {line(7f,7f,10f,4f);line(10f,4f,13f,7f);line(7f,13f,10f,16f);line(10f,16f,13f,13f)}
            "search" -> {drawCircle(color,5.5f*u,Offset(8.5f*u,8.5f*u),style=Stroke(1.4f*u));line(13f,13f,17f,17f)}
            "refresh" -> {drawArc(color,40f,290f,false,Offset(3*u,3*u),androidx.compose.ui.geometry.Size(14*u,14*u),style=Stroke(1.4f*u));line(17f,4f,17f,9f);line(12f,9f,17f,9f)}
            "settings" -> {line(3f,6f,17f,6f);line(3f,14f,17f,14f);line(7f,3f,7f,9f);line(13f,11f,13f,17f)}
            else -> {line(4f,10f,16f,10f);line(12f,6f,16f,10f);line(12f,14f,16f,10f)}
        }
    }
}

@Composable internal fun Wordmark(compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF45404F),Color(0xFF2D2B33))))
            .border(1.dp,Color(0xFF625B70),RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) {
            Copy("P",18,DarkPalette.text,FontWeight.SemiBold)
        }
        if(!compact) Copy("Professor OS",14,weight=FontWeight.SemiBold)
    }
}
