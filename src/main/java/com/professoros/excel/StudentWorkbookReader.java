package com.professoros.excel;
import com.professoros.model.DomainValidationException;
import com.professoros.repository.StorageException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Bounded .xlsx reader; does not execute formulas, macros or resolve external links. */
public final class StudentWorkbookReader {
    public StudentWorkbook read(Path file) {
        if(file==null || file.getFileName()==null || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) { throw new DomainValidationException("Choose an .xlsx file, not a folder, .xls or .xlsm file."); }
        if(!Files.isRegularFile(file)) { throw new DomainValidationException("Workbook not found or is a folder. Browse to an existing .xlsx file."); }
        try {
            if(Files.size(file)>20L*1024*1024) { throw new DomainValidationException("Workbook exceeds the 20 MB limit."); }
            // Inspect actual expanded bytes before POI allocates XML objects. Retain POI's own ZIP protections too.
            try(var zip=new ZipFile(file.toFile())) {
                long total=0; int entries=0; byte[] buffer=new byte[8192];
                var iterator=zip.entries();
                while(iterator.hasMoreElements()) {
                    var entry=iterator.nextElement(); if(++entries>2000) { throw new DomainValidationException("Workbook contains too many ZIP parts."); }
                    if(entry.getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) { throw new DomainValidationException("Macro-enabled workbooks are not supported."); }
                    try(var input=zip.getInputStream(entry)) { int read; while((read=input.read(buffer))!=-1) { total+=read; if(total>64L*1024*1024) { throw new DomainValidationException("Workbook expands beyond the 64 MB limit."); } } }
                }
            }
            try(var pkg=OPCPackage.open(file.toFile(),PackageAccess.READ); var workbook=new XSSFWorkbook(pkg)) {
                if(workbook.getNumberOfSheets()>20) { throw new DomainValidationException("Choose a workbook with at most 20 sheets."); }
                var formatter=new DataFormatter(Locale.ROOT);
                List<StudentWorkbook.Sheet> sheets=new ArrayList<>(); int cells=0;
                for(var sheet:workbook) {
                    if(sheet.getLastRowNum()>10000) { throw new DomainValidationException("Sheets may contain at most 10,000 data rows plus one header."); }
                    List<StudentWorkbook.Row> rows=new ArrayList<>();
                    for(var row:sheet) {
                        if(row.getLastCellNum()>100) { throw new DomainValidationException("Sheets may contain at most 100 columns."); }
                        List<StudentWorkbook.Value> values=new ArrayList<>();
                        for(int i=0;i<row.getLastCellNum();i++) {
                            if(++cells>200000) { throw new DomainValidationException("Workbook exceeds the 200,000-cell limit."); }
                            Cell cell=row.getCell(i); String error="",text=""; boolean numeric=false;
                            if(cell!=null) {
                                switch(cell.getCellType()) {
                                    case FORMULA -> { text="[Formula]"; error="Formula cells are not imported. Replace with reviewed values."; }
                                    case ERROR -> { text="[Excel error]"; error="Excel error cell."; }
                                    case NUMERIC -> {
                                        numeric=true; double value=cell.getNumericCellValue(); text=formatter.formatCellValue(cell);
                                        if(DateUtil.isCellDateFormatted(cell) || !Double.isFinite(value) || value<0 || value!=Math.rint(value) || value>=1e15) {
                                            error="Numeric identifiers must be nonnegative whole numbers with at most 15 digits. Set the Excel column to Text, then re-enter the original ID; formatting alone cannot restore lost digits.";
                                        } else if(!text.matches("[0-9]+") || new java.math.BigInteger(text).compareTo(java.math.BigDecimal.valueOf(value).toBigIntegerExact())!=0) {
                                            error="Numeric cell format changes or abbreviates the value. Set the Excel column to Text, then re-enter the complete original ID (including leading zeros).";
                                        }
                                    }
                                    case BOOLEAN -> { text=formatter.formatCellValue(cell); error="Boolean cells cannot be student fields."; }
                                    default -> text=formatter.formatCellValue(cell);
                                }
                                if(text.length()>5000) { error="Cell exceeds 5,000 characters."; text=text.substring(0,5000); }
                            }
                            values.add(new StudentWorkbook.Value(text,error,numeric));
                        }
                        if(values.stream().anyMatch(v->!v.text().isBlank() || !v.error().isBlank())) { rows.add(new StudentWorkbook.Row(row.getRowNum()+1,values)); }
                    }
                    sheets.add(new StudentWorkbook.Sheet(sheet.getSheetName(),rows));
                }
                if(sheets.isEmpty() || sheets.stream().allMatch(s->s.rows().isEmpty())) { throw new DomainValidationException("The workbook contains no populated sheets."); }
                return new StudentWorkbook(sheets);
            }
        } catch(DomainValidationException failure) { throw failure; }
        catch(Exception failure) { throw new StorageException("Cannot read this workbook. Use an unencrypted, valid .xlsx file within the size limits.",failure); }
    }
}
