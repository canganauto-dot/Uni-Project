package com.professoros.model;
import java.math.BigDecimal;
public record AttendanceSummary(int present,int absent,int late,int excused,int unmarked,BigDecimal percentage) {
    public String display() { return (percentage==null?"Not yet rated":percentage+"%")+" · Present "+present+" · Absent "+absent+" · Late "+late+" · Excused "+excused+" · Unmarked "+unmarked; }
}
