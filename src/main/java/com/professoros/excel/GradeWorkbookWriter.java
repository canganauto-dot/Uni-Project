package com.professoros.excel;
import com.professoros.model.*;
import com.professoros.service.GradeCalculator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Read-only report snapshots. All grading decisions come from the existing calculator, never Excel formulas. */
public final class GradeWorkbookWriter {
    private CellStyle body,number,integer,percent,header,title,note,date;
    public byte[] write(Gradebook book,String offering) throws IOException { return write(book,offering,null); }
    public byte[] write(Gradebook book,String offering,AttendanceBook attendance) throws IOException {
        if(book.assessments().size()>1000 || book.students().size()>10000 || (long)(book.assessments().size()+8)*book.students().size()>200000) { throw new DomainValidationException("Excel export supports up to 1,000 assessments, 10,000 students and 200,000 gradebook cells."); }
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            styles(workbook);
            var scores=GradeCalculator.scores(book);
            List<String> columns=new ArrayList<>(List.of("Student ID","Student name","Enrollment"));
            book.assessments().forEach(a->columns.add(a.title()+" / "+a.maximumMarks()));
            columns.addAll(List.of("Weighted points /100","Final percentage","Letter grade","Result","Graded / required"));
            Sheet grades=sheet(workbook,"Grades",offering,columns,GradeCalculator.configurationMessage(book));
            int rowIndex=6;
            for(var student:book.students()) {
                Row row=grades.createRow(rowIndex++); row.setHeightInPoints(30); int col=0;
                text(row,col++,student.student().studentNumber()); text(row,col++,student.student().fullName()); text(row,col++,student.enrollment().status().toString());
                for(var assessment:book.assessments()) { decimal(row,col++,scores.get(new GradeKey(student.enrollment().id(),assessment.id())),number); }
                var result=GradeCalculator.calculate(book,student.enrollment().id(),scores);
                decimal(row,col++,result.weightedPoints(),number);
                decimal(row,col++,result.complete()?result.weightedPoints().movePointLeft(2):null,percent);
                text(row,col++,result.complete()?result.letter():""); text(row,col++,result.outcome());
                text(row,col,result.graded()+" / "+result.required());
            }
            finish(grades,columns.size(),rowIndex); grades.createFreezePane(2,6);
            Sheet breakdown=sheet(workbook,"Assessment Breakdown",offering,List.of("Category","Weight %","Assessment","Date","Maximum marks","Graded students","Class mean marks"),
                    "Class means exclude withdrawn enrollments and ungraded cells; actual zero scores are included.");
            rowIndex=6;
            var eligible=book.students().stream().filter(s->s.enrollment().status()!=EnrollmentStatus.WITHDRAWN).map(s->s.enrollment().id()).collect(java.util.stream.Collectors.toSet());
            for(var category:book.categories()) {
                var assessments=book.assessments().stream().filter(a->a.categoryId()==category.id()).toList();
                if(assessments.isEmpty()) { Row row=breakdown.createRow(rowIndex++); text(row,0,category.name()); decimal(row,1,category.weight().movePointLeft(2),percent); text(row,2,"No assessments"); }
                for(var assessment:assessments) {
                    Row row=breakdown.createRow(rowIndex++); row.setHeightInPoints(32);
                    text(row,0,category.name()); decimal(row,1,category.weight().movePointLeft(2),percent); text(row,2,assessment.title());
                    Cell day=row.createCell(3); day.setCellValue(assessment.date()); day.setCellStyle(date);
                    decimal(row,4,assessment.maximumMarks(),number);
                    var values=book.grades().stream().filter(g->g.key().assessmentId()==assessment.id() && eligible.contains(g.key().enrollmentId())).map(StudentGrade::score).toList();
                    decimal(row,5,BigDecimal.valueOf(values.size()),integer);
                    decimal(row,6,values.isEmpty()?null:values.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(values.size()),2,RoundingMode.HALF_UP),number);
                }
            }
            finish(breakdown,7,rowIndex);
            Sheet statistics=sheet(workbook,"Statistics",offering,List.of("Metric","Value","Meaning"),
                    "Saved grade statistics. Attendance, when supplied, is reported separately.");
            var stats=GradeCalculator.classStatistics(book,scores); int index=6;
            metric(statistics,index++,"Complete results",BigDecimal.valueOf(stats.complete()),"ACTIVE and COMPLETED enrollments",integer);
            metric(statistics,index++,"Incomplete results",BigDecimal.valueOf(stats.incomplete()),"Excluded from mean and pass/fail rate",integer);
            metric(statistics,index++,"Class average",stats.mean()==null?null:stats.mean().movePointLeft(2),"Complete results only; same rounded percentages as Gradebook",percent);
            metric(statistics,index++,"Minimum",stats.minimum()==null?null:stats.minimum().movePointLeft(2),"Complete results only",percent);
            metric(statistics,index++,"Maximum",stats.maximum()==null?null:stats.maximum().movePointLeft(2),"Complete results only",percent);
            metric(statistics,index++,"Passed",BigDecimal.valueOf(stats.passed()),"Withdrawn enrollments excluded",integer);
            metric(statistics,index++,"Failed",BigDecimal.valueOf(stats.complete()-stats.passed()),"Complete results only",integer);
            metric(statistics,index++,"Pass rate",stats.complete()==0?null:BigDecimal.valueOf(stats.passed()).divide(BigDecimal.valueOf(stats.complete()),8,RoundingMode.HALF_UP),"Passed / complete results",percent);
            metric(statistics,index++,"Category weights",GradeCalculator.weightTotal(book).movePointLeft(2),"Must total 100% for final results",percent);
            var scale=book.scale();
            for(var entry:List.of(Map.entry("A minimum",scale.a()),Map.entry("B minimum",scale.b()),Map.entry("C minimum",scale.c()),Map.entry("D minimum",scale.d()),Map.entry("Pass minimum",scale.pass()))) {
                metric(statistics,index++,entry.getKey(),entry.getValue().movePointLeft(2),"Inclusive threshold; classification precedes display rounding",percent);
            }
            text(statistics.createRow(index++),0,"F is below D. Missing grades are not zeros. Within categories, assessments are weighted by maximum marks.");
            statistics.setColumnWidth(0,38*256); statistics.setColumnWidth(1,20*256); statistics.setColumnWidth(2,64*256); finish(statistics,3,index);
            if(attendance!=null) attendance(workbook,offering,attendance);
            workbook.write(output); return output.toByteArray();
        }
    }
    private void attendance(XSSFWorkbook workbook,String offering,AttendanceBook book) {
        if(book.lectures().size()>1000 || (long)(book.lectures().size()+9)*book.students().size()>200000)
            throw new DomainValidationException("Attendance export supports up to 1,000 lectures and 200,000 cells.");
        List<String> columns=new ArrayList<>(List.of("Student ID","Student name","Enrollment"));
        book.lectures().forEach(l->columns.add(l.plannedDate()+" · "+l.lectureNumber()+". "+l.title()+(l.status()==LectureStatus.CANCELLED?" (cancelled)":"")));
        columns.addAll(List.of("Present","Absent","Late","Excused","Unmarked","Attendance %"));
        Sheet sheet=sheet(workbook,"Attendance",offering,columns,com.professoros.service.AttendanceCalculator.POLICY);
        sheet.getRow(4).getCell(0).setCellValue("Blank = unmarked, never Absent. Cancelled columns preserve history but do not affect totals. Withdrawn students remain visible; course summary excludes them.");
        int index=6;
        for(var student:book.students()) {
            Row row=sheet.createRow(index++);int col=0;long enrollment=student.enrollment().id();
            text(row,col++,student.student().studentNumber());text(row,col++,student.student().fullName());text(row,col++,student.enrollment().status().name());
            for(var lecture:book.lectures()) {var status=book.status(enrollment,lecture.id());text(row,col++,status==null?"":status.name());}
            var total=com.professoros.service.AttendanceCalculator.student(book,enrollment);
            for(int count:new int[]{total.present(),total.absent(),total.late(),total.excused(),total.unmarked()}) decimal(row,col++,BigDecimal.valueOf(count),integer);
            decimal(row,col,total.percentage()==null?null:total.percentage().movePointLeft(2),percent);
        }
        finish(sheet,columns.size(),index);sheet.createFreezePane(2,6);
        text(sheet.createRow(index+1),0,"Course summary (withdrawn excluded): "+com.professoros.service.AttendanceCalculator.course(book).display());
    }
    private void metric(Sheet sheet,int index,String label,BigDecimal value,String meaning,CellStyle style) {
        Row row=sheet.createRow(index); row.setHeightInPoints(32); text(row,0,label); decimal(row,1,value,style); text(row,2,meaning);
    }
    private Sheet sheet(XSSFWorkbook workbook,String name,String offering,List<String> columns,String warning) {
        Sheet sheet=workbook.createSheet(name); sheet.setDisplayGridlines(false);
        text(sheet.createRow(0),0,"Professor OS — "+name); sheet.getRow(0).getCell(0).setCellStyle(title); sheet.getRow(0).setHeightInPoints(28);
        text(sheet.createRow(1),0,offering);
        text(sheet.createRow(2),0,"Saved snapshot "+Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)+" — editing cells does not recalculate results.");
        text(sheet.createRow(3),0,warning);
        text(sheet.createRow(4),0,"Blank scores are ungraded; 0 is an actual score. Weighted points may be provisional; final percentage/letter are blank until complete.");
        // Spanning report notes are unmerged, allowing their text to flow over empty cells.
        for(int i=1;i<=4;i++) { sheet.getRow(i).getCell(0).setCellStyle(note); sheet.getRow(i).setHeightInPoints(22); }
        Row headers=sheet.createRow(5); headers.setHeightInPoints(42);
        for(int i=0;i<columns.size();i++) { text(headers,i,columns.get(i)); headers.getCell(i).setCellStyle(header); sheet.setColumnWidth(i,(i==1?30:24)*256); }
        sheet.createFreezePane(0,6); sheet.setRepeatingRows(new CellRangeAddress(0,5,-1,-1));
        sheet.getPrintSetup().setLandscape(true); sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)0); sheet.setFitToPage(true);
        sheet.getFooter().setRight("Page &P of &N");
        return sheet;
    }
    private void finish(Sheet sheet,int columns,int rows) {
        sheet.setAutoFilter(new CellRangeAddress(5,Math.max(5,rows-1),0,columns-1));
        for(int i=6;i<rows;i++) {
            Row row=sheet.getRow(i); float height=Math.max(26,row.getHeightInPoints());
            for(Cell cell:row) { if(cell.getCellType()==CellType.STRING && cell.getCellStyle().getWrapText()) {
                int width=Math.max(8,sheet.getColumnWidth(cell.getColumnIndex())/256-2);
                height=Math.max(height,16*(1+cell.getStringCellValue().length()/width));
            } }
            row.setHeightInPoints(Math.min(300,height));
        }
        if(rows==6) { text(sheet.createRow(6),0,"No saved records."); }
    }
    private void text(Row row,int col,String value) { Cell cell=row.createCell(col); if(!value.isEmpty()) { cell.setCellValue(value); } cell.setCellStyle(body); }
    private void decimal(Row row,int col,BigDecimal value,CellStyle style) { Cell cell=row.createCell(col); if(value!=null) { cell.setCellValue(value.doubleValue()); } cell.setCellStyle(style); }
    private void styles(XSSFWorkbook wb) {
        Font font=wb.createFont(); font.setFontName("Arial"); font.setFontHeightInPoints((short)11);
        body=wb.createCellStyle(); body.setFont(font); body.setVerticalAlignment(VerticalAlignment.CENTER); body.setWrapText(true); body.setDataFormat(wb.createDataFormat().getFormat("@")); body.setAlignment(HorizontalAlignment.LEFT); body.setIndention((short)1);
        number=wb.createCellStyle(); number.cloneStyleFrom(body); number.setDataFormat(wb.createDataFormat().getFormat("0.00")); number.setAlignment(HorizontalAlignment.RIGHT);
        integer=wb.createCellStyle(); integer.cloneStyleFrom(number); integer.setDataFormat(wb.createDataFormat().getFormat("0"));
        percent=wb.createCellStyle(); percent.cloneStyleFrom(number); percent.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
        date=wb.createCellStyle(); date.cloneStyleFrom(body); date.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
        Font white=wb.createFont(); white.setFontName("Arial"); white.setFontHeightInPoints((short)11); white.setBold(true); white.setColor(IndexedColors.WHITE.getIndex());
        header=wb.createCellStyle(); header.cloneStyleFrom(body); header.setFont(white); header.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND); header.setAlignment(HorizontalAlignment.CENTER); header.setBorderRight(BorderStyle.THIN); header.setRightBorderColor(IndexedColors.WHITE.getIndex());
        Font large=wb.createFont(); large.setFontName("Arial"); large.setFontHeightInPoints((short)16); large.setBold(true);
        title=wb.createCellStyle(); title.setFont(large);
        note=wb.createCellStyle(); note.setFont(font); note.setWrapText(false);
    }
}
