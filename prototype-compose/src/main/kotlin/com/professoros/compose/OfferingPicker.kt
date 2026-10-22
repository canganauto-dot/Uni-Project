package com.professoros.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable internal fun OfferingPicker(snapshot:DashboardSnapshot?,reduced:Boolean,dismissible:Boolean=false,select:(Long)->Unit) {
    var query by remember {mutableStateOf("")}
    var cursor by remember(query) {mutableStateOf(0)}
    val focus=remember {FocusRequester()}
    val rows=remember(snapshot?.offerings,query) {
        snapshot?.offerings.orEmpty().filter { (it.label()+" "+it.courseName()).contains(query.trim(),ignoreCase=true) }
    }
    val scroll=rememberLazyListState()
    LaunchedEffect(Unit){focus.requestFocus()}
    LaunchedEffect(cursor) {if(rows.isNotEmpty())scroll.scrollToItem(cursor.coerceAtMost(rows.lastIndex))}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.onPreviewKeyEvent {
        if(it.type!=KeyEventType.KeyDown || rows.isEmpty()) false else when(it.key) {
            Key.DirectionDown -> {cursor=(cursor+1)%rows.size;true}
            Key.DirectionUp -> {cursor=(cursor-1+rows.size)%rows.size;true}
            Key.Enter -> {select(rows[cursor.coerceAtMost(rows.lastIndex)].offering().id());true}
            else -> false
        }
    }) {
        Row(Modifier.fillMaxWidth().background(Ink.background,RoundedCornerShape(8.dp))
            .border(1.dp,Ink.border,RoundedCornerShape(8.dp)).padding(12.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Mark("search",size=16)
            BasicTextField(query,{query=it},singleLine=true,textStyle=TextStyle(color=Ink.text,fontFamily=fontFor(query),fontSize=13.sp),
                cursorBrush=SolidColor(Ink.violet),
                modifier=Modifier.weight(1f).focusRequester(focus).testTag("offering-search").semantics{contentDescription="Find a course, term or section"},
                decorationBox={inner -> Box {if(query.isEmpty())Copy("Find a course, term or section…",13,Ink.muted);inner()} })
        }
        if(rows.isEmpty()) {
            Copy(if(snapshot?.offerings.isNullOrEmpty())"No offerings yet" else "No matching offerings",16,weight=FontWeight.SemiBold)
            Copy(if(snapshot?.offerings.isNullOrEmpty())"Create a course offering in the full workspace, then refresh." else "Try a course code, name or academic term.",13,Ink.muted)
        } else LazyColumn(Modifier.fillMaxWidth().heightIn(max=260.dp),state=scroll,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            itemsIndexed(rows,key={_,row->row.offering().id()}) { index,row ->
                val interaction=remember {MutableInteractionSource()}
                val hovered by interaction.collectIsHoveredAsState()
                val focused by interaction.collectIsFocusedAsState()
                val active=snapshot?.selected?.offering()?.id()==row.offering().id()
                val surface by animateColorAsState(if(hovered||index==cursor)Ink.raised else Ink.panel,motion(reduced),label="offering-hover")
                Row(Modifier.fillMaxWidth().background(surface,RoundedCornerShape(8.dp))
                    .border(1.dp,if(focused)Ink.violet else Ink.panel,RoundedCornerShape(8.dp))
                    .onFocusChanged {if(it.isFocused)cursor=index}
                    .hoverable(interaction).clickable(interactionSource=interaction,indication=null,role=Role.Button){select(row.offering().id())}
                    .testTag("offering-"+row.offering().id()).semantics {selected=active}
                    .padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Copy(row.courseName(),14,weight=FontWeight.Medium,lines=2)
                        Copy(row.label(),11,Ink.muted,lines=2)
                    }
                    if(active) Mark("check",Ink.violet,16)
                }
            }
        }
        Copy("↑ ↓ to browse   ·   Enter to select" + if(dismissible) "   ·   Esc to dismiss picker" else "",10,Ink.muted)
    }
}
