package com.professoros.model;

import java.util.List;

/** One load supplies consistent list/timeline and progress from the same rows. */
public record LectureOverview(List<Lecture> lectures, LectureProgress progress) {
    public LectureOverview { lectures = List.copyOf(lectures); }
}
