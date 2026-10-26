package com.professoros.demo;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Repeatable application import fixtures; intentionally includes one unsafe-input test workbook. */
public final class DemoImportSamples {
    private DemoImportSamples() { }
    public static void write(Path directory) throws Exception {
        write(directory,LocalDate.now().getYear());
    }
    public static void write(Path directory,int year) throws Exception {
        Files.createDirectory(directory); // Never overwrite earlier fixtures or user exports.
        var english = new ArrayList<List<String>>();
        var arabic = new ArrayList<List<String>>();
        String[] given={"Sophie","Benjamin","Clara","Samuel","Isabel","Nathan"};
        String[] family={"Brooks","Collins","Hayes","Turner"};
        String[] arGiven={"ياسر","سلمى","بلال","آية","أنس","جنى"};
        String[] arFamily={"التميمي","الدليمي","الراوي","الشمري"};
        for(int i=0;i<24;i++) {
            english.add(List.of(String.format(Locale.ROOT,"00990%03d",i+1),given[i%6],family[i/6],"incoming"+(i+1)+"@example.edu","A"));
            arabic.add(List.of(String.format(Locale.ROOT,"00991%03d",i+1),arGiven[i%6]+" "+arFamily[i/6],"arabic"+(i+1)+"@example.edu","A"));
        }
        workbook(directory.resolve("students-english.xlsx"),List.of("Student ID","First name","Last name","Email","Section"),english,false);
        workbook(directory.resolve("students-arabic.xlsx"),List.of("رقم الطالب","اسم الطالب","البريد الإلكتروني","الشعبة"),arabic,false);
        workbook(directory.resolve("students-manual-mapping.xlsx"),List.of("Registration reference","Learner","Contact address","Tutorial"),
            List.of(List.of("00992001","Nadia Faris","nadia@example.edu","A"),List.of("00992002","Hugo Ellis","","A")),false);
        workbook(directory.resolve("students-validation-cases.xlsx"),List.of("Student ID","Full name","Email","Section"),List.of(
            List.of("00"+year+"001","أحمد العبيدي","","A"),
            List.of("00993001","Leila Faris","leila@example.edu","A"),
            List.of("00993001","Different Name","different@example.edu","A"),
            List.of("","Missing Identifier","","A"),
            List.of("00993002","","","A"),
            List.of("00993003","Invalid Email","not-an-email","A"),
            List.of("00993004","Numeric Identifier","","A"),
            List.of("00993005","Formula Identifier","","A")),true);
        Files.writeString(directory.resolve("README.md"), """
            # Synthetic student import fixtures

            All people and addresses are fictional. No real student data is included.
            - students-english.xlsx: 24 new students, first/last names, IDs stored as text.
            - students-arabic.xlsx: 24 new students, Arabic headers/full names, IDs stored as text.
            - students-manual-mapping.xlsx: 2 new students; map all four nonstandard headers manually.
            - students-validation-cases.xlsx: existing ID, repeated new ID, blank ID/name, invalid email,
              formatted numeric ID and a formula ID. Formula rejection is intentional; no external links/macros.
            Use Preview before confirming. The existing ID must be skipped, not overwritten. Reimporting
            successful files should skip existing profiles; importing into an offering can enroll them.
            Number formatting is only a display hint; prefer text IDs to preserve leading zeros.
            Samples are independent from persisted demo records except the intentional existing-ID row.
            """);
    }
    private static void workbook(Path path,List<String> headers,List<List<String>> rows,boolean edgeCases) throws Exception {
        try(var book=new XSSFWorkbook()) {
            book.getProperties().getCoreProperties().setTitle("Professor OS — synthetic import fixture");
            var sheet=book.createSheet("Students");sheet.createFreezePane(0,1);
            var heading=book.createCellStyle();heading.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());heading.setFont(font);
            var text=book.createCellStyle();text.setDataFormat(book.createDataFormat().getFormat("@"));
            var header=sheet.createRow(0);header.setHeightInPoints(25);
            for(int c=0;c<headers.size();c++){var cell=header.createCell(c);cell.setCellValue(headers.get(c));cell.setCellStyle(heading);sheet.setColumnWidth(c,(c==0?18:c==headers.size()-1?14:30)*256);}
            for(int r=0;r<rows.size();r++) {
                var row=sheet.createRow(r+1);row.setHeightInPoints(22);
                for(int c=0;c<headers.size();c++){var cell=row.createCell(c);cell.setCellValue(rows.get(r).get(c));if(c==0)cell.setCellStyle(text);}
            }
            if(edgeCases) {
                var numeric=sheet.getRow(7).getCell(0);numeric.setCellValue(993004);
                var numericStyle=book.createCellStyle();numericStyle.setDataFormat(book.createDataFormat().getFormat("00000000"));numeric.setCellStyle(numericStyle);
                sheet.getRow(8).getCell(0).setCellFormula("1+1");
            }
            sheet.setAutoFilter(new CellRangeAddress(0,rows.size(),0,headers.size()-1));
            try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){book.write(out);}
        }
    }
}
