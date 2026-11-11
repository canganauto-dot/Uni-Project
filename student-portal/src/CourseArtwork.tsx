import type { Course } from "./demo";
import courseArtwork from "./assets/studio-courses.jpg";

const artworkPosition: Record<string, [number, number]> = {
    "human-computer-interaction": [0, 0],
    "data-structures": [960, 0],
    "visual-computing": [0, 480],
    "research-methods": [960, 480],
};

export function CourseArtwork({ course, className = "" }: { course: Course; className?: string }) {
    const position = artworkPosition[course.id];
    if (!position) return null;
    const [x, y] = position;
    return (
        <svg className={`studio-art ${className}`} aria-hidden="true"
            viewBox={`${x} ${y} 960 480`} preserveAspectRatio="xMidYMid slice">
            <image href={courseArtwork} width="1920" height="960" />
        </svg>
    );
}
