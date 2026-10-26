package com.professoros.demo;

import java.util.List;

/** Fictional curriculum, kept out of runtime services and application startup. */
final class DemoCurriculum {
    record Topic(String name, String principle, String exercise) { }
    record Subject(String code, String name, List<Topic> topics) { }
    static final List<Subject> SUBJECTS = List.of(
        new Subject("CS201", "Object-Oriented Programming", List.of(
            new Topic("Encapsulation", "Private state is changed through validated public methods", "implement a BankAccount that rejects overdrafts"),
            new Topic("Polymorphism", "An interface permits different implementations behind the same contract", "implement Shape with Circle and Rectangle"),
            new Topic("Collections", "A set stores unique elements while a list preserves a sequence", "remove duplicate student numbers while preserving order"),
            new Topic("Exceptions", "Resource ownership and explicit failures make cleanup predictable", "read a UTF-8 roster using try-with-resources"))),
        new Subject("CS202", "Data Structures and Algorithms", List.of(
            new Topic("Binary search", "Binary search halves the search interval in a sorted array", "return the index of a target in a sorted integer array"),
            new Topic("Stacks and queues", "A queue serves elements in first-in-first-out order", "simulate a help-desk queue"),
            new Topic("Hash tables", "Collisions require a strategy such as chaining or probing", "count word frequencies using a map"),
            new Topic("Graph traversal", "Breadth-first search finds shortest paths in an unweighted graph", "find the shortest route between two campus buildings"))),
        new Subject("CS203", "Database Systems", List.of(
            new Topic("Relational modeling", "Foreign keys preserve relationships between rows in different tables", "model a course offering and its enrollments using SQL DDL"),
            new Topic("Normalization", "Third normal form removes transitive dependencies on a key", "decompose an order table into customers, orders and items"),
            new Topic("SQL joins", "A left join preserves unmatched rows from its left relation", "list students including those without an enrollment"),
            new Topic("Transactions", "Atomicity ensures a transaction commits all changes or none", "transfer funds between two accounts in one transaction"))),
        new Subject("SE301", "Software Engineering", List.of(
            new Topic("Requirements", "Acceptance criteria make a requirement observable and testable", "write executable examples for a registration rule"),
            new Topic("Design cohesion", "A cohesive module groups closely related responsibilities", "separate validation from a user-interface event handler"),
            new Topic("Unit testing", "A unit test isolates a small behavior with explicit expectations", "test score validation at zero and maximum marks"),
            new Topic("Version control", "Small commits and reviewable changes make history understandable", "write a script that checks a project before release"))),
        new Subject("CS305", "Computer Networks", List.of(
            new Topic("Layered protocols", "Layering separates responsibilities and provides stable interfaces", "parse the fields of a simplified network packet"),
            new Topic("IP addressing", "A subnet mask separates network and host address bits", "determine whether two IPv4 addresses share a subnet"),
            new Topic("Reliable transport", "Acknowledgments and retransmissions recover from packet loss", "simulate retransmission after an acknowledgment timeout"),
            new Topic("Application protocols", "HTTP requests and responses carry method, status and header information", "parse an HTTP status line without network access"))),
        new Subject("HCI310", "Human–Computer Interaction / تفاعل الإنسان والحاسوب", List.of(
            new Topic("Usability", "Task success, time and errors provide observable usability evidence", "summarize success rates from usability-test observations"),
            new Topic("Accessibility", "Keyboard navigation and meaningful labels improve access", "implement a keyboard-driven selection model"),
            new Topic("Interaction feedback", "Visible feedback helps users understand the result of an action", "model loading, success and error states"),
            new Topic("Evaluation", "Representative tasks reveal problems that visual preference alone misses", "calculate the median completion time for a study")))
    );
    private DemoCurriculum() { }
}
