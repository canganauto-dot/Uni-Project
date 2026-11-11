import { useState } from "react";
import { motion, useReducedMotion } from "motion/react";
import {
    ArrowLeft,
    ArrowRight,
    BookOpen,
    CalendarDays,
    ChartNoAxesCombined,
    Check,
    ChevronDown,
    CircleHelp,
    Search,
} from "lucide-react";
import {
    courses,
    weeklyAttendance,
    type Course,
} from "./demo";
import {
    CourseArt,
    CourseCard,
    Meter,
    PageIntro,
    Panel,
    Ring,
    SectionHeading,
} from "./ui";

function PreviewState({ state, retry }: { state: string; retry: () => void }) {
    if (state === "loading")
        return (
            <div className="state-card" role="status" aria-busy="true">
                <div className="skeleton-grid">
                    <span />
                    <span />
                    <span />
                </div>
                <strong>Loading course preview…</strong>
                <p>This is a visual loading state; no data is being fetched.</p>
            </div>
        );
    return (
        <div
            className="state-card"
            role={state === "error" ? "alert" : "status"}
        >
            <span className="state-icon">
                {state === "error" ? (
                    <CircleHelp size={25} />
                ) : (
                    <Search size={25} />
                )}
            </span>
            <h2>
                {state === "error"
                    ? "Courses could not be shown"
                    : "No courses match"}
            </h2>
            <p>
                {state === "error"
                    ? "This is a preview of how a future data error would appear."
                    : "Try a different search term or category."}
            </p>
            <button className="button-secondary" onClick={retry}>
                {state === "error" ? "Return to courses" : "Clear filters"}
            </button>
        </div>
    );
}
export function Courses() {
    const [query, setQuery] = useState("");
    const [category, setCategory] = useState("All subjects");
    const [preview, setPreview] = useState("data");
    const filtered = courses.filter(
        (c) =>
            (category === "All subjects" || c.category === category) &&
            `${c.title} ${c.code} ${c.instructor}`
                .toLowerCase()
                .includes(query.toLowerCase()),
    );
    const reset = () => {
        setQuery("");
        setCategory("All subjects");
        setPreview("data");
    };
    return (
        <>
            <PageIntro
                eyebrow="YOUR LEARNING"
                title="My courses"
                description="A clear view of everything you're exploring this semester."
                action={
                    <span className="count-chip">
                        {courses.length} active courses
                    </span>
                }
            />
            <div className="toolbar">
                <label className="search-box">
                    <Search size={18} />
                    <span className="sr-only">Search courses</span>
                    <input
                        value={query}
                        onChange={(e) => {
                            setQuery(e.target.value);
                            setPreview("data");
                        }}
                        placeholder="Search courses or instructors"
                        type="search"
                    />
                </label>
                <label className="select-wrap">
                    <span className="sr-only">Filter by subject</span>
                    <select
                        value={category}
                        onChange={(e) => {
                            setCategory(e.target.value);
                            setPreview("data");
                        }}
                    >
                        <option>All subjects</option>
                        {courses.map((c) => (
                            <option key={c.id}>{c.category}</option>
                        ))}
                    </select>
                    <ChevronDown size={16} aria-hidden="true" />
                </label>
                <label className="select-wrap preview-select">
                    <span className="sr-only">Preview display state</span>
                    <select
                        value={preview}
                        onChange={(e) => setPreview(e.target.value)}
                    >
                        <option value="data">Course view</option>
                        <option value="loading">Loading preview</option>
                        <option value="empty">Empty preview</option>
                        <option value="error">Error preview</option>
                    </select>
                    <ChevronDown size={16} aria-hidden="true" />
                </label>
            </div>
            {preview !== "data" ? (
                <PreviewState state={preview} retry={reset} />
            ) : filtered.length === 0 ? (
                <PreviewState state="empty" retry={reset} />
            ) : (
                <div className="courses-grid listing-grid">
                    {filtered.map((c, index) => (
                        <CourseCard key={c.id} course={c} order={index} />
                    ))}
                </div>
            )}
        </>
    );
}

export function CourseDetail({ course }: { course: Course }) {
    return (
        <>
            <a href="#/courses" className="back-link">
                <ArrowLeft size={16} /> All courses
            </a>
            <div className="detail-hero">
                <div>
                    <span className="eyebrow">
                        {course.category.toUpperCase()} · {course.code}
                    </span>
                    <h1>{course.title}</h1>
                    <p>{course.description}</p>
                    <span className="detail-teacher">
                        <span className="mini-avatar">
                            {course.instructor
                                .split(" ")
                                .slice(-1)[0]
                                .charAt(0)}
                        </span>{" "}
                        {course.instructor}{" "}
                        <span className="detail-separator">·</span>{" "}
                        {course.credits} credits
                    </span>
                </div>
                <CourseArt course={course} />
            </div>
            <div className="detail-grid">
                <Panel>
                    <SectionHeading
                        label="AT A GLANCE"
                        title="Course journey"
                    />
                    <div className="detail-metrics">
                        <Ring
                            value={course.progress}
                            label="complete"
                            tone={course.color}
                        />
                        <div className="detail-metric-list">
                            <div>
                                <span>Current result</span>
                                <strong>
                                    {course.grade}{" "}
                                    <small>{course.score}%</small>
                                </strong>
                            </div>
                            <div>
                                <span>Attendance</span>
                                <strong>{course.attendance}%</strong>
                            </div>
                            <div>
                                <span>Credits</span>
                                <strong>{course.credits}</strong>
                            </div>
                        </div>
                    </div>
                </Panel>
                <Panel>
                    <SectionHeading label="UP NEXT" title="Stay in rhythm" />
                    <div className="next-event">
                        <span className="stat-icon violet">
                            <CalendarDays size={21} />
                        </span>
                        <div>
                            <strong>{course.next.split(" · ")[0]}</strong>
                            <p>{course.next.split(" · ")[1]}</p>
                        </div>
                    </div>
                    <p className="muted-note">
                        This schedule is fictional demo content. Future course
                        details will come from the student portal service.
                    </p>
                    <a className="text-link" href="#/grades">
                        See all grades <ArrowRight size={16} />
                    </a>
                </Panel>
            </div>
        </>
    );
}

export function Grades() {
    const reduceMotion = useReducedMotion();
    return (
        <>
            <PageIntro
                eyebrow="ACADEMIC RECORD"
                title="Grades & results"
                description="A thoughtful view of your progress, course by course."
            />
            <div className="grades-summary">
                <Panel className="grade-feature">
                    <span className="eyebrow">CURRENT TERM GPA</span>
                    <div className="grade-feature-number">
                        3.78<span>/ 4.0</span>
                    </div>
                    <p>Looking good, Amina. Keep the momentum going.</p>
                    <div
                        className="grade-feature-decoration"
                        aria-hidden="true"
                    >
                        A
                    </div>
                </Panel>
                <Panel className="grade-mini">
                    <span className="stat-icon blue">
                        <BookOpen size={21} />
                    </span>
                    <span>Courses graded</span>
                    <strong>4</strong>
                    <p>Fall 2026 term</p>
                </Panel>
                <Panel className="grade-mini">
                    <span className="stat-icon teal">
                        <ChartNoAxesCombined size={21} />
                    </span>
                    <span>Average score</span>
                    <strong>86.5%</strong>
                    <p>Across current courses</p>
                </Panel>
            </div>
            <Panel className="result-panel">
                <SectionHeading label="FALL 2026" title="Course results" />
                <div className="result-table-wrap">
                    <table className="result-table">
                        <thead>
                            <tr>
                                <th scope="col">Course</th>
                                <th scope="col">Credits</th>
                                <th scope="col">Score</th>
                                <th scope="col">Result</th>
                                <th scope="col">
                                    <span className="sr-only">View</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {courses.map((c) => (
                                <tr key={c.id} className={`course-tone-${c.color}`}>
                                    <td>
                                        <span
                                            className={`table-course-icon ${c.color}`}
                                        >
                                            <CourseArt course={c} />
                                        </span>
                                        <span>
                                            <strong>{c.title}</strong>
                                            <small>
                                                {c.code} · {c.instructor}
                                            </small>
                                        </span>
                                    </td>
                                    <td data-label="Credits">{c.credits}</td>
                                    <td data-label="Score">
                                        <span className="score-line">
                                            <motion.span className="score-track"
                                                initial={reduceMotion ? false : "empty"}
                                                whileInView="filled"
                                                viewport={{ once: true }}>
                                                <motion.span
                                                    style={{
                                                        width: `${c.score}%`,
                                                    }}
                                                    variants={{ empty: { scaleX: 0 }, filled: { scaleX: 1 } }}
                                                    transition={{ duration: reduceMotion ? 0 : 0.8, ease: [0.16, 1, 0.3, 1] }}
                                                />
                                            </motion.span>
                                            {c.score}%
                                        </span>
                                    </td>
                                    <td data-label="Result">
                                        <span className="grade-badge">
                                            {c.grade}
                                        </span>
                                    </td>
                                    <td>
                                        <a
                                            href={`#/courses/${c.id}`}
                                            aria-label={`View ${c.title}`}
                                        >
                                            <ArrowRight size={18} />
                                        </a>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
                <p className="table-footnote">
                    Illustrative results only. Grading rules and official
                    records are not connected.
                </p>
            </Panel>
        </>
    );
}

export function Attendance() {
    const reduceMotion = useReducedMotion();
    return (
        <>
            <PageIntro
                eyebrow="SHOWING UP MATTERS"
                title="Attendance"
                description="See your presence across the semester at a glance."
            />
            <div className="attendance-layout">
                <Panel className="attendance-main">
                    <SectionHeading
                        label="FALL 2026"
                        title="Weekly attendance"
                        action={<span className="small-tag">12 weeks</span>}
                    />
                    <div className="chart-heading">
                        <strong>93%</strong>
                        <span>average attendance</span>
                    </div>
                    <div
                        className="bar-chart"
                        role="img"
                        aria-label={`Fictional attendance by week: ${weeklyAttendance.join(", ")} percent`}
                    >
                        <div className="chart-lines" aria-hidden="true">
                            <span>100%</span>
                            <span>50%</span>
                            <span>0%</span>
                        </div>
                        <div className="bars">
                            {weeklyAttendance.map((value, index) => (
                                <div className="bar-column" key={index} title={`Week ${index + 1} · ${value}%`}>
                                    <motion.div className="bar-track"
                                        initial={reduceMotion ? false : "empty"}
                                        whileInView="filled"
                                        viewport={{ once: true }}>
                                        <motion.span style={{ height: `${value}%` }}
                                            variants={{ empty: { scaleY: 0 }, filled: { scaleY: 1 } }}
                                            transition={{ duration: reduceMotion ? 0 : 0.65, delay: reduceMotion ? 0 : index * 0.025, ease: [0.16, 1, 0.3, 1] }} />
                                    </motion.div>
                                    <small>{index + 1}</small>
                                </div>
                            ))}
                        </div>
                    </div>
                    <p className="chart-caption">
                        Week number · attendance percentages are fictional
                    </p>
                </Panel>
                <Panel className="attendance-side">
                    <span className="eyebrow">YOUR CONSISTENCY</span>
                    <Ring value={93} label="average" tone="teal" />
                    <h2>Great rhythm.</h2>
                    <p>
                        Every session is another opportunity to grow. Keep
                        showing up for yourself.
                    </p>
                    <div className="attendance-side-foot">
                        <Check size={16} /> 3 courses above 90%
                    </div>
                </Panel>
            </div>
            <Panel className="attendance-courses">
                <SectionHeading label="BY COURSE" title="Course attendance" />
                <div className="attendance-list">
                    {courses.map((c) => (
                        <div className={`attendance-row course-tone-${c.color}`} key={c.id}>
                            <div>
                                <strong>{c.title}</strong>
                                <small>{c.code}</small>
                            </div>
                            <div className="attendance-meter">
                                <Meter value={c.attendance} color={c.color} label="attendance" />
                            </div>
                            <strong>{c.attendance}%</strong>
                        </div>
                    ))}
                </div>
            </Panel>
        </>
    );
}
