package com.professoros.excel;
import java.text.Normalizer;
import java.util.*;
public final class StudentColumns {
    private StudentColumns() { }
    public enum Field {
        ID("Student ID"),FULL_NAME("Full name"),FIRST_NAME("First name"),LAST_NAME("Last name"),EMAIL("Email"),SECTION("Section");
        private final String label; Field(String label) { this.label=label; } @Override public String toString() { return label; }
    }
    private static final Map<Field,List<String>> ALIASES=Map.of(
        Field.ID,List.of("studentid","studentnumber","studentno","id","رقمالطالب","الرقمالجامعي","الرقمالطلابي","الرقم","رقمالقيد"),
        Field.FULL_NAME,List.of("fullname","studentname","name","اسمالطالب","الاسمالكامل","الاسم"),
        Field.FIRST_NAME,List.of("firstname","givenname","الاسمالاول"),
        Field.LAST_NAME,List.of("lastname","surname","familyname","اسمالعائلة","الاسمالاخير","اللقب"),
        Field.EMAIL,List.of("email","emailaddress","البريدالالكتروني","الايميل"),
        Field.SECTION,List.of("section","group","الشعبة","المجموعة","شعبة"));
    private static String normalize(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{M}\\s_\\-./()]+","")
                .replace("أ","ا").replace("إ","ا").replace("آ","ا").replace("ى","ي").replace("ـ","");
    }
    public static Map<Field,Integer> detect(StudentWorkbook.Row header) {
        Map<Field,Integer> result=new EnumMap<>(Field.class);
        for(var field:Field.values()) {
            List<Integer> matches=new ArrayList<>();
            for(int i=0;i<header.cells().size();i++) { String name=normalize(header.cell(i).text()); if(ALIASES.get(field).contains(name)) { matches.add(i); } }
            if(matches.size()==1) { result.put(field,matches.getFirst()); } // Ambiguous headers require manual mapping.
        }
        return result;
    }
    public static int headerRow(StudentWorkbook.Sheet sheet) {
        return sheet.rows().stream().filter(r->r.number()<=30).max(Comparator.comparingInt(r->detect(r).size())).map(StudentWorkbook.Row::number).orElse(1);
    }
}
