import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { motion, useReducedMotion } from "motion/react";
import {
    ArrowRight,
    BookOpen,
    CalendarDays,
    ChartNoAxesCombined,
    ChevronRight,
    GraduationCap,
    LayoutDashboard,
    LockKeyhole,
    Menu,
    PanelLeftClose,
    PanelLeftOpen,
    UserRound,
    X,
    type LucideIcon,
} from "lucide-react";
import { student, type Course } from "./demo";
import { CourseArtwork } from "./CourseArtwork";

export const nav: { label: string; href: string; icon: LucideIcon }[] = [
    { label: "Overview", href: "#/dashboard", icon: LayoutDashboard },
    { label: "My courses", href: "#/courses", icon: BookOpen },
    { label: "Grades", href: "#/grades", icon: ChartNoAxesCombined },
    { label: "Attendance", href: "#/attendance", icon: CalendarDays },
    { label: "Profile & security", href: "#/profile", icon: UserRound },
];

export function Brand({ onClick }: { onClick?: () => void } = {}) {
    return (
        <a
            className="brand"
            href="#/dashboard"
            onClick={onClick}
            aria-label="Professor OS student portal overview"
        >
            <span className="brand-icon">
                <GraduationCap size={23} strokeWidth={2.1} />
            </span>
            <span>
                <strong>
                    professor<span className="text-accent">os</span>
                </strong>
                <small>STUDENT SPACE</small>
            </span>
        </a>
    );
}

export function isCurrentRoute(route: string, href: string) {
    const path = href.slice(1);
    return route === path || (path === "/courses" && route.startsWith("/courses/"));
}

function NavigationItems({ route, close }: { route: string; close?: () => void }) {
    return (
        <nav className="sidebar-nav" aria-label="Student pages">
            {nav.map(({ label, href, icon: Icon }) => {
                const active = isCurrentRoute(route, href);
                return (
                    <a key={href} href={href} onClick={close}
                        className="nav-link" aria-label={label} title={label}
                        aria-current={active ? (route === href.slice(1) ? "page" : "location") : undefined}>
                        <Icon size={19} aria-hidden="true" />
                        <span className="rail-label">{label}</span>
                    </a>
                );
            })}
        </nav>
    );
}

function NavigationFooter({ close }: { close?: () => void }) {
    return (
        <div className="sidebar-bottom">
            <a className="nav-link" href="#/login" onClick={close}
                aria-label="Login UI preview" title="Login UI preview">
                <LockKeyhole size={18} aria-hidden="true" />
                <span className="rail-label">Login UI preview</span>
            </a>
        </div>
    );
}

export function Sidebar({ route, open, close }: {
    route: string;
    open: boolean;
    close: () => void;
}) {
    const [expanded, setExpanded] = useState(false);
    const toggle = useRef<HTMLButtonElement>(null);
    const drawer = useRef<HTMLDialogElement>(null);
    const drawerClose = useRef<HTMLButtonElement>(null);
    useLayoutEffect(() => {
        const dialog = drawer.current;
        if (!dialog) return;
        if (open && !dialog.open) {
            dialog.showModal();
            drawerClose.current?.focus({ preventScroll: true });
        }
        else if (!open && dialog.open) dialog.close();
    }, [open]);

    return (
        <>
            <aside className={expanded ? "sidebar rail-expanded" : "sidebar"}
                aria-label="Main navigation"
                onBlur={(event) => {
                    if (!event.currentTarget.contains(event.relatedTarget)) setExpanded(false);
                }}
                onKeyDown={(event) => {
                    if (event.key === "Escape" && expanded) {
                        setExpanded(false);
                        toggle.current?.focus();
                    }
                }}>
                <Brand />
                <button className="nav-link rail-toggle" type="button" ref={toggle}
                    onClick={() => setExpanded((previous) => !previous)}
                    aria-label={expanded ? "Collapse navigation" : "Expand navigation"}
                    title={expanded ? "Collapse navigation" : "Expand navigation"}
                    aria-expanded={expanded} aria-controls="desktop-navigation">
                    {expanded ? <PanelLeftClose size={19} /> : <PanelLeftOpen size={19} />}
                    <span className="rail-label">Collapse navigation</span>
                </button>
                <div id="desktop-navigation">
                    <NavigationItems route={route} />
                </div>
                <NavigationFooter />
            </aside>

            <dialog ref={drawer} className="mobile-drawer" id="student-navigation"
                aria-label="Student navigation" onClose={(event) => {
                    if (!event.currentTarget.open) close();
                }}
                onClick={(event) => {
                    if (event.target === event.currentTarget) close();
                }}>
                <div className="drawer-panel">
                    <div className="drawer-heading">
                        <Brand onClick={close} />
                        <button ref={drawerClose} className="icon-button drawer-close"
                            type="button" onClick={close} aria-label="Close menu">
                            <X size={20} />
                        </button>
                    </div>
                    <NavigationItems route={route} close={close} />
                    <NavigationFooter close={close} />
                </div>
            </dialog>
        </>
    );
}

export function Topbar({
    route,
    menuOpen,
    openMenu,
}: {
    route: string;
    menuOpen: boolean;
    openMenu: () => void;
}) {
    const title = route.startsWith("/courses/")
        ? "Course details"
        : (
              {
                  "/dashboard": "Overview",
                  "/courses": "My courses",
                  "/grades": "Grades",
                  "/attendance": "Attendance",
                  "/profile": "Profile & security",
              } as Record<string, string>
          )[route] || "Overview";
    return (
        <header className="topbar">
            <div className="flex items-center gap-3">
                <button
                    className="icon-button menu-trigger"
                    type="button"
                    onClick={openMenu}
                    aria-label="Open menu"
                    aria-expanded={menuOpen}
                    aria-controls="student-navigation"
                    aria-haspopup="dialog"
                >
                    <Menu size={22} />
                </button>
                <div className="topbar-breadcrumb">
                    {route === "/dashboard" ? (
                        <strong>Professor OS</strong>
                    ) : (
                        <>
                            <span>Student space</span>
                            <ChevronRight size={15} />
                            <strong>{title}</strong>
                        </>
                    )}
                </div>
            </div>
            <div className="topbar-actions">
                <span className="demo-pill">
                    <span /> Fictional demo
                </span>
                <a
                    className="avatar"
                    href="#/profile"
                    aria-label="Open fictional student profile"
                >
                    {student.initials}
                </a>
            </div>
        </header>
    );
}

export function PageIntro({
    eyebrow,
    title,
    description,
    action,
}: {
    eyebrow: string;
    title: string;
    description: string;
    action?: ReactNode;
}) {
    return (
        <div className="page-intro">
            <div>
                <div className="eyebrow">{eyebrow}</div>
                <h1>{title}</h1>
                <p>{description}</p>
            </div>
            {action}
        </div>
    );
}

export function Panel({
    children,
    className = "",
}: {
    children: ReactNode;
    className?: string;
}) {
    return <section className={`panel ${className}`}>{children}</section>;
}
export function SectionHeading({
    label,
    title,
    action,
}: {
    label?: string;
    title: string;
    action?: ReactNode;
}) {
    return (
        <div className="section-heading">
            <div>
                {label && <span className="eyebrow">{label}</span>}
                <h2>{title}</h2>
            </div>
            {action}
        </div>
    );
}
export function Meter({
    value,
    color = "violet",
    label = "complete",
}: {
    value: number;
    color?: string;
    label?: string;
}) {
    const reduceMotion = useReducedMotion();
    return (
        <motion.div
            className="meter"
            role="meter"
            aria-valuenow={value}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`${value}% ${label}`}
            initial={reduceMotion ? false : "empty"}
            whileInView="filled"
            viewport={{ once: true }}
        >
            <motion.span
                className={`meter-fill ${color}`}
                style={{ width: `${value}%` }}
                variants={{ empty: { scaleX: 0 }, filled: { scaleX: 1 } }}
                transition={{ duration: reduceMotion ? 0 : 0.8, ease: [0.16, 1, 0.3, 1] }}
            />
        </motion.div>
    );
}
export function Ring({
    value,
    label,
    tone = "violet",
}: {
    value: number;
    label: string;
    tone?: string;
}) {
    return (
        <div
            className={`ring ${tone}`}
            style={{ "--value": `${value}%` } as React.CSSProperties}
            role="img"
            aria-label={`${label}: ${value}%`}
        >
            <div>
                <strong>
                    {value}
                    <small>%</small>
                </strong>
                <span>{label}</span>
            </div>
        </div>
    );
}

export function CourseArt({ course }: { course: Course }) {
    return (
        <div className={`course-art art-${course.color}`} aria-hidden="true">
            <CourseArtwork course={course} />
        </div>
    );
}
export function CourseCard({ course, order = 0 }: { course: Course; order?: number }) {
    const reduceMotion = useReducedMotion();
    return (
        <motion.article className={`course-card course-tone-${course.color}`}
            initial={reduceMotion ? false : { opacity: 0.5, y: 12 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: reduceMotion ? 0 : 0.42, delay: reduceMotion ? 0 : order * 0.065, ease: [0.16, 1, 0.3, 1] }}>
            <CourseArt course={course} />
            <div className="course-body">
                <div className="flex items-center justify-between gap-3">
                    <span className="course-code">{course.code}</span>
                    <span className="course-credits">
                        {course.credits} credits
                    </span>
                </div>
                <h3>
                    <a href={`#/courses/${course.id}`}>{course.title}</a>
                </h3>
                <p>{course.instructor}</p>
                <div className="course-progress">
                    <div className="flex items-center justify-between">
                        <span>Course progress</span>
                        <strong>{course.progress}%</strong>
                    </div>
                    <Meter value={course.progress} color={course.color} />
                </div>
                <a className="course-open" href={`#/courses/${course.id}`}>
                    View course <ArrowRight size={16} />
                </a>
            </div>
        </motion.article>
    );
}
