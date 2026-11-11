import { useEffect, useState } from "react";
import { AnimatePresence, motion, MotionConfig, useReducedMotion } from "motion/react";
import { courses } from "./demo";
import { isCurrentRoute, nav, Sidebar, Topbar } from "./ui";
import {
    Attendance,
    CourseDetail,
    Courses,
    Grades,
} from "./AcademicPages";
import { Dashboard } from "./Dashboard";
import { AuthPreview, NotFound, Profile } from "./AccountPages";
import "./studio-pages.css";

function useRoute() {
    const [route, setRoute] = useState(
        window.location.hash.slice(1) || "/dashboard",
    );
    useEffect(() => {
        const update = () =>
            setRoute(window.location.hash.slice(1) || "/dashboard");
        window.addEventListener("hashchange", update);
        return () => window.removeEventListener("hashchange", update);
    }, []);
    return route;
}

function resetRouteScroll(node: HTMLDivElement | null) {
    if (node) window.scrollTo({ top: 0, behavior: "instant" });
}

export default function App() {
    const route = useRoute();
    const reduceMotion = useReducedMotion();
    const [menuOpen, setMenuOpen] = useState(false);
    useEffect(() => {
        const mobile = window.matchMedia("(max-width: 800px)");
        const closeDrawer = () => setMenuOpen(false);
        const resize = () => {
            if (!mobile.matches) closeDrawer();
        };
        mobile.addEventListener("change", resize);
        window.addEventListener("hashchange", closeDrawer);
        return () => {
            mobile.removeEventListener("change", resize);
            window.removeEventListener("hashchange", closeDrawer);
        };
    }, []);
    if (route === "/login" || route === "/mfa")
        return <MotionConfig reducedMotion="user">
            <AnimatePresence mode="wait" initial={false}>
                <motion.div key={route} ref={resetRouteScroll} initial={{ opacity: 0, y: reduceMotion ? 0 : 10 }}
                    animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: reduceMotion ? 0 : -8 }}
                    transition={{ duration: reduceMotion ? 0 : 0.3 }}>
                    <AuthPreview mfa={route === "/mfa"} />
                </motion.div>
            </AnimatePresence>
        </MotionConfig>;
    const detail = route.startsWith("/courses/")
        ? courses.find((c) => c.id === route.slice("/courses/".length))
        : undefined;
    return (
        <MotionConfig reducedMotion="user">
        <div className={route === "/dashboard" ? "app-shell studio-shell" : "app-shell"}>
            <button
                className="skip-link"
                onClick={() => document.getElementById("main-content")?.focus()}
            >
                Skip to content
            </button>
            <Sidebar
                route={route}
                open={menuOpen}
                close={() => setMenuOpen(false)}
            />
            <div className={route === "/courses" || detail || ["/grades", "/attendance", "/profile"].includes(route)
                ? "app-main studio-pages" : "app-main"}>
                <Topbar
                    route={route}
                    menuOpen={menuOpen}
                    openMenu={() => setMenuOpen(true)}
                />
                <main id="main-content" className="page-content" tabIndex={-1}>
                    <div className={route === "/dashboard" ? "sr-only" : "demo-notice"}>
                        <span className="demo-notice-dot" />
                        <span>
                            Preview mode: all student names, grades, and
                            schedules are fictional.
                        </span>
                    </div>
                    <AnimatePresence mode="wait">
                    <motion.div key={route} ref={resetRouteScroll} className="route-view"
                        initial={reduceMotion ? false : { opacity: 0.4, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={reduceMotion ? undefined : {
                            opacity: 0.72,
                            transition: { duration: 0.09, ease: "easeIn" },
                        }}
                        transition={{ duration: reduceMotion ? 0 : 0.32, ease: [0.16, 1, 0.3, 1] }}>
                    {route === "/dashboard" ? (
                        <Dashboard />
                    ) : route === "/courses" ? (
                        <Courses />
                    ) : detail ? (
                        <CourseDetail course={detail} />
                    ) : route === "/grades" ? (
                        <Grades />
                    ) : route === "/attendance" ? (
                        <Attendance />
                    ) : route === "/profile" ? (
                        <Profile />
                    ) : (
                        <NotFound />
                    )}
                    </motion.div>
                    </AnimatePresence>
                </main>
            </div>
            <nav className="mobile-nav" aria-label="Mobile navigation">
                {nav.slice(0, 4).map(({ label, href, icon: Icon }) => (
                    <a
                        key={href}
                        href={href}
                        aria-label={label}
                        aria-current={
                            isCurrentRoute(route, href) ? (route === href.slice(1) ? "page" : "location") : undefined
                        }
                        className={
                            isCurrentRoute(route, href) ? "active" : ""
                        }
                    >
                        <Icon size={20} />
                        <span>
                            {label === "My courses" ? "Courses" : label}
                        </span>
                    </a>
                ))}
            </nav>
        </div>
        </MotionConfig>
    );
}
