package com.professoros.compose

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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

@Composable internal fun Input(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier,multiline:Boolean=false,tag:String=label) {
    var focused by remember {mutableStateOf(false)}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Copy(label,12,Ink.muted)
        BasicTextField(value,change,textStyle=TextStyle(color=Ink.text,fontFamily=fontFor(value),fontSize=14.sp,textDirection=TextDirection.Content),
            cursorBrush=SolidColor(Ink.violet),singleLine=!multiline,readOnly=!LocalControlsEnabled.current,
            modifier=Modifier.fillMaxWidth().heightIn(min=if(multiline)100.dp else 38.dp,max=if(multiline)180.dp else 60.dp)
                .onFocusChanged {focused=it.isFocused}.background(Ink.background,RoundedCornerShape(8.dp))
                .border(1.dp,if(focused)Ink.violet else Ink.border,RoundedCornerShape(8.dp)).padding(10.dp)
                .testTag(tag).semantics {contentDescription=label})
    }
}

@Composable internal fun Select(label:String,value:String,choices:List<Choice>,change:(String)->Unit,tag:String=label) {
    var expanded by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Copy(label,12,Ink.muted)
        Box {
            Action(choices.firstOrNull {it.value==value}?.label?:"Select…","chevrons",modifier=Modifier.fillMaxWidth().testTag(tag)) {expanded=true;query=""}
            if(expanded) Popup(alignment=Alignment.TopStart,onDismissRequest={expanded=false},properties=PopupProperties(focusable=true)) {
                Panel(Modifier.width(370.dp).heightIn(max=350.dp).onPreviewKeyEvent {
                    if(it.type==KeyEventType.KeyDown && it.key==Key.Escape){expanded=false;true}else false
                }) {
                    val focus=remember {FocusRequester()}
                    Input("Find an option",query,{query=it},Modifier.focusRequester(focus),tag=tag+"-search")
                    LaunchedEffect(Unit){focus.requestFocus()}
                    LazyColumn(Modifier.heightIn(max=220.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        items(choices.filter {it.label.contains(query,true)}) { option ->
                            Action(option.label,if(option.value==value)"check" else "arrow",modifier=Modifier.fillMaxWidth().testTag(tag+"-"+option.value)) {change(option.value);expanded=false}
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun Toolbar(content:@Composable FlowRowScope.()->Unit) = FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)

data class TableColumn<T>(val title:String,val weight:Float=1f,val text:(T)->String)
@Composable internal fun <T> RecordTable(rows:List<T>,columns:List<TableColumn<T>>,id:(T)->Long,selected:Long?,select:(T)->Unit,open:(T)->Unit,tag:String,empty:String="No records yet. Create one to get started.") {
    var sort by remember {mutableStateOf(-1)}; var descending by remember {mutableStateOf(false)}
    val sorted=remember(rows,sort,descending) { if(sort<0)rows else rows.sortedWith(compareBy<T>{columns[sort].text(it).lowercase()}.let {if(descending)it.reversed() else it}) }
    Column(Modifier.fillMaxWidth().border(1.dp,Ink.border,RoundedCornerShape(10.dp)).testTag(tag)) {
        Row(Modifier.fillMaxWidth().background(Ink.raised).padding(8.dp)) {
            columns.forEachIndexed {index,column->
                Box(Modifier.weight(column.weight).clickable {if(sort==index)descending=!descending else {sort=index;descending=false}}.padding(6.dp)) {
                    Copy(column.title+if(sort==index)if(descending)" ↓" else " ↑" else "",11,Ink.muted,FontWeight.Medium)
                }
            }
        }
        if(rows.isEmpty()) Copy(empty,14,Ink.muted,modifier=Modifier.padding(24.dp))
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max=340.dp)) {
            items(sorted,key=id) {row ->
                val interactions=remember {MutableInteractionSource()}
                val hovered by interactions.collectIsHoveredAsState();val focused by interactions.collectIsFocusedAsState()
                Row(Modifier.fillMaxWidth().background(if(selected==id(row))Ink.selected else if(hovered)Ink.raised else Ink.panel)
                    .border(1.dp,if(focused)Ink.violet else Ink.panel)
                    .hoverable(interactions).onPreviewKeyEvent {if(it.type==KeyEventType.KeyDown && it.key==Key.Enter){select(row);open(row);true}else false}
                    .clickable(interactionSource=interactions,indication=null,enabled=LocalControlsEnabled.current){select(row)}.testTag(tag+"-"+id(row))
                    .semantics {this.selected=selected==id(row)}.padding(horizontal=8.dp,vertical=13.dp)) {
                    columns.forEach {column->Copy(column.text(row),13,modifier=Modifier.weight(column.weight).padding(horizontal=6.dp),lines=2)}
                }
                Rule()
            }
        }
    }
}

/** Focusable modal popup prevents keyboard or pointer actions reaching the editing background. */
@Composable internal fun Modal(title:String,close:()->Unit,wide:Boolean=false,footer:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width=(maxWidth-48.dp).coerceAtMost(if(wide)1040.dp else 620.dp)
        val height=(maxHeight-48.dp).coerceAtMost(800.dp)
        Popup(alignment=Alignment.Center,onDismissRequest=close,properties=PopupProperties(focusable=true)) {
            Column(Modifier.width(width).heightIn(max=height).background(Ink.panel,RoundedCornerShape(14.dp))
                .border(1.dp,Ink.border,RoundedCornerShape(14.dp)).padding(22.dp).testTag("editor-modal")
                .onPreviewKeyEvent {if(it.type==KeyEventType.KeyDown && it.key==Key.Escape){close();true}else false},verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Copy(title,20,weight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Action("Close",onClick=close)
                }
                Rule()
                Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
                Rule();footer()
            }
        }
    }
}

@Composable internal fun WorkspaceOverlays(c:WorkspaceController,reduced:Boolean) {
    val form=c.form
    if(form!=null) Modal(form.title,c::closeEditor,footer={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("form-error"))}
            Toolbar {
                Action(if(c.busy)"Saving…" else "Save","check",reduced=reduced,enabled=!c.busy,modifier=Modifier.testTag("save-form"),onClick=c::saveForm)
                Action("Cancel",enabled=!c.busy,onClick=c::closeEditor)
            }
        }
    }) {
        CompositionLocalProvider(LocalControlsEnabled provides !c.busy) { form.fields.forEachIndexed {index,field->
            val focus=remember {FocusRequester()}
            if(field.choices!=null) Select(field.label,form[field.key],field.choices,{form.values[field.key]=it},"field-"+field.key)
            else Input(field.label,form[field.key],{form.values[field.key]=it},Modifier.focusRequester(focus),field.multiline,"field-"+field.key)
            if(index==0 && field.choices==null) LaunchedEffect(form){focus.requestFocus()}
        } }
    }
    c.detail?.let { detail-> Modal(detail.title,{c.detail=null},footer={Action("Done"){c.detail=null}}) {Copy(detail.text,14)} }
    c.confirmation?.let { confirmation-> Modal(confirmation.title,{c.confirmation=null},footer={Toolbar {
        Action("Keep / cancel",modifier=Modifier.testTag("cancel-confirm")){c.confirmation=null}
        Action("Confirm","check",modifier=Modifier.testTag("confirm")){c.confirmation=null;confirmation.proceed()}
    }}) {Copy(confirmation.message,14)} }
}
