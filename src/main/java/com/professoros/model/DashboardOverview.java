package com.professoros.model;
import java.time.*;
import java.util.List;

/** Read-only dashboard summary; counts are saved records, never synthetic activity. */
public record DashboardOverview(int students,int offerings,Integer activeEnrollments,LectureProgress progress,
                                List<Activity> upcoming,List<Activity> recentlyUpdated,LocalDate asOf) {
    public DashboardOverview { upcoming=List.copyOf(upcoming); recentlyUpdated=List.copyOf(recentlyUpdated); }
    public record Activity(String kind,String title,LocalDate date,String detail,Instant updatedAt) { }
}
