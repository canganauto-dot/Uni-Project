package com.professoros.service;
import com.professoros.excel.*;
import com.professoros.model.*;
import com.professoros.repository.*;
import java.time.*;
import java.util.*;
import static com.professoros.excel.StudentColumns.Field.*;

public final class StudentImportService {
    private final StudentImportRepository imports;
    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final CourseOfferingRepository offerings;
    private final AcademicTermRepository terms;
    public StudentImportService(StudentImportRepository imports,StudentRepository students,EnrollmentRepository enrollments,
                                CourseOfferingRepository offerings,AcademicTermRepository terms) {
        this.imports=imports; this.students=students; this.enrollments=enrollments; this.offerings=offerings; this.terms=terms;
    }
    public StudentImportPreview preview(StudentWorkbook.Sheet sheet,int headerRow,Map<StudentColumns.Field,Integer> mapping,Long offeringId) {
        if(!mapping.containsKey(ID) || !(mapping.containsKey(FULL_NAME) || (mapping.containsKey(FIRST_NAME) && mapping.containsKey(LAST_NAME)))) {
            throw new DomainValidationException("Map Student ID and either Full name or both First name and Last name.");
        }
        if(new HashSet<>(mapping.values()).size()!=mapping.size() || mapping.values().stream().anyMatch(i->i<0 || i>=100)) {
            throw new DomainValidationException("Map each field to a different valid column.");
        }
        if(sheet.rows().stream().noneMatch(r->r.number()==headerRow)) { throw new DomainValidationException("Choose a populated header row."); }
        String section="";
        if(offeringId!=null) {
            var offering=offerings.findById(offeringId).orElseThrow(()->AcademicValidation.missing("Offering"));
            var term=terms.findById(offering.academicTermId()).orElseThrow(()->AcademicValidation.missing("Term"));
            if(closed(offering.status()) || closed(term.status())) { throw new DomainValidationException("Import enrollment requires a planned or active offering and term."); }
            section=offering.section();
        }
        Map<String,Student> existing=new HashMap<>();
        students.findAll().forEach(s->existing.put(s.studentNumber().toUpperCase(Locale.ROOT),s));
        Set<Long> enrolled=new HashSet<>();
        if(offeringId!=null) { enrollments.findStudentsForOffering(offeringId).forEach(s->enrolled.add(s.student().id())); }
        Map<String,Long> frequencies=new HashMap<>();
        for(var row:sheet.rows()) { if(row.number()>headerRow) { frequencies.merge(id(value(row,mapping,ID)),1L,Long::sum); } }
        List<StudentImportRow> result=new ArrayList<>();
        for(var row:sheet.rows()) {
            if(row.number()<=headerRow) { continue; }
            if(mapping.values().stream().allMatch(i->row.cell(i).text().isBlank() && row.cell(i).error().isBlank())) { continue; }
            String number=id(value(row,mapping,ID)),first=value(row,mapping,FIRST_NAME),last=value(row,mapping,LAST_NAME);
            String rowSection=value(row,mapping,SECTION).strip().toUpperCase(Locale.ROOT),message="";
            if(first.isBlank() && last.isBlank() && mapping.containsKey(FULL_NAME)) {
                var name=value(row,mapping,FULL_NAME).strip().split("\\s+",2); first=name[0]; last=name.length>1?name[1]:"";
                message="Full name split into first word and remaining name. ";
            }
            StudentInput input=new StudentInput(number,first,last,value(row,mapping,EMAIL),
                    offeringId==null && !rowSection.isBlank()?"Imported section: "+rowSection:"");
            Student old=existing.get(number);
            var action=StudentImportRow.Action.CREATE;
            try {
                for(var index:mapping.values()) { if(!row.cell(index).error().isEmpty()) { throw new DomainValidationException(row.cell(index).error()); } }
                input=StudentService.validate(input);
                rowSection=AcademicValidation.text(rowSection,"Section",40,false);
                if(frequencies.getOrDefault(number,0L)>1) { throw new DomainValidationException("Duplicate student ID within this sheet; resolve all repeated rows."); }
                if(offeringId!=null && !rowSection.isBlank() && !rowSection.equalsIgnoreCase(section)) { throw new DomainValidationException("Section "+rowSection+" does not match selected section "+section+"."); }
                if(old!=null) {
                    if(!old.firstName().equalsIgnoreCase(input.firstName()) || !old.lastName().equalsIgnoreCase(input.lastName())
                            || (!input.email().isBlank() && !old.email().equalsIgnoreCase(input.email()))) {
                        throw new DomainValidationException("ID exists with different name/email. No student will be overwritten.");
                    }
                    action=offeringId!=null && !enrolled.contains(old.id())?StudentImportRow.Action.ENROLL:StudentImportRow.Action.SKIP;
                    message+=action==StudentImportRow.Action.ENROLL?"Enroll existing student; profile unchanged.":"Already exists/enrolled; no changes.";
                } else { message+=offeringId==null?"Create student.":"Create student and enroll."; }
                if(row.cell(mapping.get(ID)).numeric()) { message+=" Numeric ID: verify against the original roster, including leading zeros. Prefer Text cells; formatting alone cannot restore digits Excel already lost."; }
                if(offeringId==null && !rowSection.isBlank()) { message+=" Section retained in new student's notes."; }
            } catch(DomainValidationException failure) { action=StudentImportRow.Action.ERROR; message=failure.getMessage(); }
            result.add(new StudentImportRow(row.number(),input,rowSection,action,message,old));
        }
        if(result.isEmpty()) { throw new DomainValidationException("No student rows below the selected header."); }
        return new StudentImportPreview(offeringId,section,result);
    }
    public StudentImportResult confirm(StudentImportPreview preview) {
        if(preview.actionable()==0) { throw new DomainValidationException("There are no valid new students or enrollments to import."); }
        for(var row:preview.rows()) {
            if(row.action()==StudentImportRow.Action.CREATE || row.action()==StudentImportRow.Action.ENROLL) { StudentService.validate(row.input()); }
        }
        return imports.commit(preview,AcademicValidation.now(Clock.systemUTC()));
    }
    private static boolean closed(AcademicStatus status) { return status==AcademicStatus.COMPLETED || status==AcademicStatus.ARCHIVED; }
    private static String value(StudentWorkbook.Row row,Map<StudentColumns.Field,Integer> map,StudentColumns.Field field) { return row.cell(map.getOrDefault(field,-1)).text().strip(); }
    private static String id(String raw) {
        StringBuilder result=new StringBuilder();
        raw.strip().codePoints().forEach(cp->{ int digit=Character.digit(cp,10); if(digit>=0) { result.append(digit); } else { result.appendCodePoint(cp); } });
        return result.toString().toUpperCase(Locale.ROOT);
    }
}
