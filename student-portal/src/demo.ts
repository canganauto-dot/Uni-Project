// Fictional records for the visual prototype. Replace this module when real data is available.
export type Course = {
    id: string;
    code: string;
    title: string;
    category: string;
    instructor: string;
    credits: number;
    progress: number;
    attendance: number;
    grade: string;
    score: number;
    color: "violet" | "blue" | "teal" | "amber";
    next: string;
    description: string;
};

export const student = {
    name: "Amina Noor",
    initials: "AN",
    id: "ST-2048-118",
    program: "BSc Computer Science",
    year: "Year 3",
    university: "Northstar University",
    email: "amina.noor@example.edu",
};

export const courses: Course[] = [
    {
        id: "human-computer-interaction",
        code: "CS 342",
        title: "Human–Computer Interaction",
        category: "Design & Technology",
        instructor: "Dr. Leila Haddad",
        credits: 3,
        progress: 76,
        attendance: 94,
        grade: "A−",
        score: 89,
        color: "violet",
        next: "Design critique · Thu, 10:00",
        description:
            "Explore how research, interaction design, and evaluation shape useful digital experiences.",
    },
    {
        id: "data-structures",
        code: "CS 301",
        title: "Data Structures & Algorithms",
        category: "Computer Science",
        instructor: "Prof. Omar Khalil",
        credits: 4,
        progress: 62,
        attendance: 88,
        grade: "B+",
        score: 84,
        color: "blue",
        next: "Lab workshop · Fri, 09:00",
        description:
            "Build efficient solutions with trees, graphs, algorithms, and complexity analysis.",
    },
    {
        id: "visual-computing",
        code: "CS 365",
        title: "Visual Computing",
        category: "Creative Computing",
        instructor: "Dr. Maya Saleh",
        credits: 3,
        progress: 83,
        attendance: 97,
        grade: "A",
        score: 93,
        color: "teal",
        next: "Studio session · Mon, 11:30",
        description:
            "Work with image formation, graphics, and visual storytelling through computation.",
    },
    {
        id: "research-methods",
        code: "RES 210",
        title: "Research Methods",
        category: "Academic Practice",
        instructor: "Dr. Yusuf Rahman",
        credits: 2,
        progress: 48,
        attendance: 91,
        grade: "B",
        score: 80,
        color: "amber",
        next: "Seminar · Tue, 13:00",
        description:
            "Develop rigorous questions, ethical methods, and clear academic communication.",
    },
];

export const weeklyAttendance = [
    88, 95, 92, 100, 91, 96, 89, 98, 94, 100, 92, 96,
];

export const activities = [
    {
        day: "13",
        month: "NOV",
        title: "Design critique",
        detail: "Human–Computer Interaction",
        time: "10:00 AM",
        tone: "violet",
    },
    {
        day: "14",
        month: "NOV",
        title: "Algorithm lab",
        detail: "Data Structures & Algorithms",
        time: "09:00 AM",
        tone: "blue",
    },
    {
        day: "17",
        month: "NOV",
        title: "Visual systems studio",
        detail: "Visual Computing",
        time: "11:30 AM",
        tone: "teal",
    },
];
