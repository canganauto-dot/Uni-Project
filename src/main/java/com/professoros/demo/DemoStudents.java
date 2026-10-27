package com.professoros.demo;

import java.util.*;

/** Curated fictional Iraqi given/father/grandfather names; never a Cartesian or numbered generator. */
final class DemoStudents {
    enum Gender { MALE, FEMALE }
    record Person(String fullName, Gender gender) {
        String[] components() { return fullName.split(" "); }
        String given() { return components()[0]; }
        String family() { return components()[1]+" "+components()[2]; }
    }
    private static Person male(String name) { return new Person(name,Gender.MALE); }
    private static Person female(String name) { return new Person(name,Gender.FEMALE); }
    static final List<Person> PEOPLE = List.of(
        male("أحمد محمد قاسم"), female("سارة علي جاسم"),
        male("علي حسين سالم"), female("مريم حسن هاشم"),
        male("عمر خالد ناظم"), female("زينب حيدر كاظم"),
        male("حسن عباس شاكر"), female("فاطمة مصطفى فاضل"),
        male("يوسف كريم حامد"), female("هبة سعد حميد"),
        male("مصطفى فؤاد رشيد"), female("رنا عادل سعيد"),
        male("خالد محمود سعد"), female("دينا ناصر عادل"),
        male("حيدر صباح عارف"), female("لينا جلال طارق"),
        male("كريم عمار سمير"), female("هدى فلاح جليل"),
        male("محمد ثامر لطيف"), female("رقية رياض كريم"),
        male("حسين عامر رحيم"), female("ضحى باسم رحمن"),
        male("سجاد راضي عباس"), female("ربى حازم حمزة"),
        male("زيد نزار ياسين"), female("آية لؤي يحيى"),
        male("مهند ياسر زكريا"), female("جنى سامي إسماعيل"),
        male("مروان سليم إبراهيم"), female("حنين رعد إسحاق"),
        male("بلال شهاب يعقوب"), female("شهد عدنان إدريس"),
        male("أنس فوزي إلياس"), female("رغد منذر أيوب"),
        male("ياسر مؤيد داود"), female("زهراء حسام سليمان"),
        male("وائل شاكر موسى"), female("إسراء مازن عيسى"),
        male("حازم كاظم نوح"), female("أسماء ناظم شعيب"),
        male("سيف جاسم هود"), female("إيمان عبدالكريم صالح"),
        male("عمار صلاح هارون"), female("سلوى غسان لقمان"),
        male("كرار قحطان يوسف"), female("بشرى كمال يونس"),
        male("عقيل وليد محمود"), female("سمية شوقي عبدالله"),
        male("نوفل فاضل عبدالحسين"), female("نرجس حامد عبدالرضا"),
        male("ليث رشيد عبدالزهرة"), female("بتول سعيد عبداللطيف"),
        male("يزن فهد عبدالكريم"), female("مروة سالم عبدالجبار"),
        male("أمجد جليل عبدالوهاب"), female("صفاء طلال عبدالرحمن"),
        male("وسام طارق عبدالقادر"), female("سجى ميثم عبدالرزاق"),
        male("هيثم نجم عبدالحليم"), female("ياسمين رافع عبدالمجيد"),
        male("رائد لؤي عبدالهادي"), female("سندس زهير عبدالسلام"),
        male("فراس ضياء عبدالصاحب"), female("تبارك كريم محمد"),
        male("زياد فلاح أحمد"), female("دعاء جبار علي"),
        male("منتظر رياض عمر"), female("أفنان عاصم حسن"),
        male("مؤيد وسام حسين"), female("رحمة خالد حيدر")
    );
    static {
        var full = new HashSet<String>(); var thirds = new HashSet<String>();
        for (var person : PEOPLE) {
            if (person.components().length!=3 || !full.add(person.fullName()) || !thirds.add(person.components()[2]))
                throw new IllegalStateException("Demo names must have three components, unique full names and unique third names");
        }
        if (PEOPLE.size()!=70 || PEOPLE.stream().filter(p->p.gender()==Gender.FEMALE).count()!=35)
            throw new IllegalStateException("Demo roster must contain 35 women and 35 men");
    }
    private DemoStudents() { }
}
