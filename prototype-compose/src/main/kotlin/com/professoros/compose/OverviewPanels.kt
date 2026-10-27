package com.professoros.compose

import com.professoros.util.PercentageFormatter

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.professoros.model.DashboardOverview
import java.time.format.DateTimeFormatter

@Composable internal fun Overview(data:DashboardSnapshot,narrow:Boolean,open:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(narrow) {
            TeachingFocus(data,Modifier.fillMaxWidth(),open)
            Library(data,Modifier.fillMaxWidth())
        } else Row(horizontalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.height(IntrinsicSize.Min)) {
            TeachingFocus(data,Modifier.weight(1.65f).fillMaxHeight(),open)
            Library(data,Modifier.weight(1f).fillMaxHeight())
        }
        if(narrow) {
            ActivityPanel("Coming up",data.overview.upcoming(),"Nothing scheduled yet",
                "Add planned lectures or an exam using the sidebar. They will appear here.")
            Attendance(data,Modifier.fillMaxWidth())
        } else Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            ActivityPanel("Coming up",data.overview.upcoming(),"Nothing scheduled yet",
                "Add planned lectures or an exam using the sidebar. They will appear here.",Modifier.weight(1.65f))
            Attendance(data,Modifier.weight(1f))
        }
        ActivityPanel("Recently updated",data.overview.recentlyUpdated(),"No recent updates",
            "Your saved lecture and exam changes will appear here.",recent=true)
    }
}

@Composable private fun TeachingFocus(data:DashboardSnapshot,modifier:Modifier,open:()->Unit) {
    val progress=data.overview.progress()
    val hasLectures=progress!=null && progress.scheduledTotal()>0
    val completion by animateFloatAsState((progress?.completionPercent()?:0.0).toFloat(),motion(),label="lecture-completion")
    Panel(modifier,tinted=true) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Mark("book",Ink.violet,16);Copy("Teaching focus",12,Ink.muted,FontWeight.Medium)
            }
            Copy(data.selected?.courseCode()?:"No offering selected",11,Ink.violet,FontWeight.Medium)
        }
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Copy(data.selected?.courseName()?:"Begin with a course offering",23,weight=FontWeight.SemiBold,lines=2)
            Copy(data.selected?.let {it.termName()+" "+it.academicYear()+"  ·  Section "+it.offering().section()}
                ?:"Choose a course, term and section to focus your workspace.",12,Ink.muted)
        }
        if(hasLectures) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
                Copy(progress.completed().toString()+" of "+progress.scheduledTotal()+" lectures delivered",13)
                Copy(PercentageFormatter.format(completion.toDouble()),24,Ink.text,FontWeight.SemiBold,Modifier.testTag("completion-value"))
            }
            Box(Modifier.fillMaxWidth().height(4.dp).background(Ink.border,RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth((completion/100).coerceIn(0f,1f)).fillMaxHeight().background(Ink.violet,RoundedCornerShape(3.dp)))
            }
            Copy(progress.scheduleStatus(),12,Ink.muted)
        } else {
            Copy(if(progress?.total()==0)"Ready for your first lecture." else if(progress==null)"Your teaching overview starts here." else "No non-cancelled lectures.",14)
            Action(if(data.selected==null) "Manage offerings" else "Open lectures","arrow",onClick=open)
        }
    }
}

@Composable private fun Library(data:DashboardSnapshot,modifier:Modifier) {
    Panel(modifier) {
        Copy("Your library",14,weight=FontWeight.SemiBold)
        listOf(Triple("Courses",data.courses,"book"),Triple("Students",data.overview.students(),"people"),
            Triple("Offerings",data.overview.offerings(),"grid")).forEachIndexed {index,(label,count,icon)->
            val number by animateIntAsState(count,motion(),label="count-"+label)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Mark(icon,size=16);Copy(label,13,Ink.muted,modifier=Modifier.weight(1f));Copy(number.toString(),21,weight=FontWeight.Medium)
            }
            if(index<2)Rule()
        }
        Copy("Across all terms and sections",11,Ink.muted)
    }
}

@Composable private fun Attendance(data:DashboardSnapshot,modifier:Modifier) {
    Panel(modifier) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Mark("people",size=16);Copy("In this offering",14,weight=FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(22.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Copy(data.overview.activeEnrollments()?.toString()?:"—",28,weight=FontWeight.Medium)
                Copy("Active students",11,Ink.muted)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Copy(data.attendance?.percentage()?.let {PercentageFormatter.format(it)}?:"—",28,Ink.positive,FontWeight.Medium)
                Copy("Attendance",11,Ink.muted)
            }
        }
        Rule()
        Copy(if(data.attendance?.percentage()==null)"Attendance is not yet rated. Unmarked records are never counted as absences."
            else "Based on saved attendance. Excused and unmarked records are excluded; Late counts fully.",12,Ink.muted)
    }
}

@Composable internal fun ActivityPanel(title:String,activities:List<DashboardOverview.Activity>,emptyTitle:String,
    emptyDetail:String,modifier:Modifier=Modifier,recent:Boolean=false) {
    Panel(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Copy(title,14,weight=FontWeight.SemiBold)
            Copy(if(recent)"Saved changes" else "Selected offering",11,Ink.muted)
        }
        if(activities.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.Top) {
                Box(Modifier.size(36.dp).background(Ink.raised,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {Mark(if(recent)"pulse" else "clock")}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Copy(emptyTitle,14,weight=FontWeight.Medium);Copy(emptyDetail,12,Ink.muted)
                }
            }
        } else activities.forEachIndexed {index,item->
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.width(42.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Copy(item.date().dayOfMonth.toString(),21,weight=FontWeight.Medium)
                    Copy(item.date().format(DateTimeFormatter.ofPattern("MMM")).uppercase(),10,Ink.muted)
                }
                Box(Modifier.width(2.dp).height(30.dp).background(if(recent)Ink.border else Ink.violet.copy(alpha=.6f),RoundedCornerShape(1.dp)))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Copy(item.title(),14,weight=FontWeight.Medium,lines=2)
                    Copy(item.kind()+" · "+item.detail(),11,Ink.muted,lines=2)
                }
                Mark(if(item.kind()=="Lecture")"book" else "clock",size=16)
            }
            if(index<activities.lastIndex)Rule()
        }
    }
}
