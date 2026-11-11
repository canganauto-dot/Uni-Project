import { ArrowRight, ArrowUpRight } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { activities, courses, student, type Course } from "./demo";
import { CourseArtwork } from "./CourseArtwork";
import "./dashboard.css";

const term = "Fall 2026";
const degreeCredits = 120;
const earnedCredits = 86;
const currentCredits = courses.reduce((total, course) => total + course.credits, 0);
const remainingCredits = degreeCredits - earnedCredits - currentCredits;
const studioEase = [0.16, 1, 0.3, 1] as const;
const onceVisible = { once: true, amount: "some" } as const;

function courseHref(course: Course) {
    return `#/courses/${course.id}`;
}

function Upcoming() {
    return (
        <section className="studio-agenda" aria-labelledby="studio-agenda-title">
            <h2 id="studio-agenda-title">Next on your calendar</h2>
            <ol>
                {activities.map((item) => (
                    <li key={item.title}>
                        <time className="studio-date" dateTime={`2026-11-${item.day}`}>
                            <strong>{item.day}</strong>
                            {item.month}
                        </time>
                        <div className="studio-event">
                            <strong>{item.title}</strong>
                            <span>{item.detail}</span>
                        </div>
                        <span className="studio-time">{item.time}</span>
                    </li>
                ))}
            </ol>
        </section>
    );
}

function CourseShelf() {
    const reduceMotion = useReducedMotion();
    return (
        <section className="studio-courses" aria-labelledby="studio-courses-title">
            <div className="studio-section-heading">
                <h2 id="studio-courses-title">Your courses</h2>
                <a className="studio-text-link" href="#/courses">
                    View all courses <ArrowRight size={16} aria-hidden="true" />
                </a>
            </div>
            <ul className="studio-shelf">
                {courses.map((course, index) => (
                    <motion.li key={course.id} className={`studio-tone-${course.color}`}
                        initial={reduceMotion ? false : { opacity: 0.4, y: 14 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={onceVisible}
                        transition={{ duration: reduceMotion ? 0 : 0.42, delay: reduceMotion ? 0 : index * 0.065, ease: studioEase }}>
                        <a className="studio-course-link" href={courseHref(course)}>
                            <span className="studio-thumbnail">
                                <CourseArtwork course={course} />
                            </span>
                            <span className="studio-course-label">
                                <span className="studio-code">{course.code}</span>
                                <ArrowUpRight size={15} aria-hidden="true" />
                            </span>
                            <strong>{course.title}</strong>
                            <span className="studio-course-progress">
                                <span className="studio-meter" aria-hidden="true">
                                    <motion.span style={{ width: `${course.progress}%` }}
                                        initial={reduceMotion ? false : { scaleX: 0 }}
                                        whileInView={{ scaleX: 1 }}
                                        viewport={onceVisible}
                                        transition={{ duration: reduceMotion ? 0 : 0.8, delay: reduceMotion ? 0 : 0.1 + index * 0.065, ease: studioEase }} />
                                </span>
                                <span>{course.progress}%<span className="sr-only"> complete</span></span>
                            </span>
                        </a>
                    </motion.li>
                ))}
            </ul>
        </section>
    );
}

function DegreeProgress() {
    const reduceMotion = useReducedMotion();
    return (
        <section className="studio-degree" aria-labelledby="studio-degree-title">
            <h2 id="studio-degree-title">Degree progress</h2>
            <p className="studio-credits">
                <strong>{earnedCredits}</strong><span>/ {degreeCredits} credits earned</span>
            </p>
            <motion.div className="studio-credit-track" aria-hidden="true"
                initial={reduceMotion ? false : { scaleX: 0 }}
                whileInView={{ scaleX: 1 }}
                viewport={onceVisible}
                transition={{ duration: reduceMotion ? 0 : 0.8, ease: studioEase }}>
                <span style={{ flex: earnedCredits }} />
                <span style={{ flex: currentCredits }} />
                <span style={{ flex: remainingCredits }} />
            </motion.div>
            <ul className="studio-credit-key">
                <li><strong>{earnedCredits}</strong> earned</li>
                <li><strong>{currentCredits}</strong> in progress</li>
                <li><strong>{remainingCredits}</strong> remaining</li>
            </ul>
            <dl className="studio-stats">
                <div>
                    <dt>Current GPA</dt>
                    <dd>3.78 <span>/ 4.0</span></dd>
                </div>
                <div>
                    <dt>Attendance</dt>
                    <dd>93<span>%</span></dd>
                </div>
            </dl>
        </section>
    );
}

export function Dashboard() {
    const featured = courses[0];
    return (
        <div className="studio-dashboard">
            <header className="studio-heading">
                <div>
                    <h1>Study dashboard</h1>
                    <p>{student.name} · {student.program}</p>
                </div>
                <span>{term}</span>
            </header>
            <div className="studio-grid">
                <section className="studio-feature" aria-labelledby="studio-feature-title">
                    <figure className="studio-feature-figure">
                        <CourseArtwork course={featured} />
                        <figcaption>Interaction study</figcaption>
                    </figure>
                    <div className="studio-feature-copy">
                        <p className="studio-code">{featured.code}</p>
                        <h2 id="studio-feature-title">{featured.title}</h2>
                        <p className="studio-feature-description">
                            Explore how people use, understand, and shape digital experiences.
                        </p>
                        <a className="studio-action" href={courseHref(featured)}>
                            Open course <ArrowRight size={17} aria-hidden="true" />
                        </a>
                    </div>
                </section>
                <Upcoming />
                <CourseShelf />
                <DegreeProgress />
            </div>
        </div>
    );
}

