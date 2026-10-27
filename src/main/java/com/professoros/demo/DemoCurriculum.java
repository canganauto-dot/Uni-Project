package com.professoros.demo;

import java.util.List;

/** Fictional curriculum, kept out of runtime services and application startup. */
final class DemoCurriculum {
    record Topic(String name, String principle, String misconception, List<String> distractors, String exercise, String solution) { }
    record Subject(String code, String name, List<Topic> topics) { }
    static final List<Subject> SUBJECTS = List.of(
        new Subject("CS201", "Object-Oriented Programming", List.of(
            new Topic("Encapsulation", "Private state is changed through validated public methods",
                "Encapsulation requires every field to be public",
                List.of("Public fields prevent invalid state", "A constructor removes the need for method validation", "Encapsulation requires a subclass for every field"),
                "implement a BankAccount with deposit and withdrawal methods that reject invalid amounts and overdrafts",
                "Keep balance private. Validate a positive amount and sufficient funds before mutation. With a balance of 20, withdrawing 25 must fail and preserve 20. Test zero, negative, exact-balance and overdraft withdrawals."),
            new Topic("Polymorphism", "An interface permits different implementations behind the same contract",
                "An overridden method is chosen only from the declared reference type, regardless of the runtime object",
                List.of("Every interface method needs an instanceof chain in its caller", "Overriding and overloading select methods in exactly the same way", "A subtype may ignore the interface contract"),
                "implement Shape with Circle and Rectangle, then calculate a list of areas without type checks",
                "Define area() on Shape. Circle returns pi*r*r and Rectangle returns width*height. Iterate over List<Shape> and call area(); dynamic dispatch selects the implementation. Reject negative dimensions and test zero-sized shapes."),
            new Topic("Collections", "A set stores unique elements while a list preserves a sequence",
                "A HashSet guarantees the insertion order of its elements",
                List.of("An ArrayList automatically removes duplicates", "Every set sorts its elements", "A HashMap rejects a repeated key rather than replacing its value"),
                "remove duplicate student numbers while preserving the first occurrence and original order",
                "Insert the numbers into a LinkedHashSet, then copy to a list. Input [S3,S1,S3,S2] becomes [S3,S1,S2]. An empty input stays empty. Explain why an unordered HashSet does not satisfy the ordering contract."),
            new Topic("Exceptions and resources", "Try-with-resources closes owned resources on normal completion and on exceptions",
                "Try-with-resources closes a reader only when its body completes normally",
                List.of("Ignoring an IOException makes a failed import successful", "A finally block is unnecessary when a resource is manually opened", "Closing a file is the caller's responsibility even when the method owns it"),
                "read a UTF-8 student roster using try-with-resources and report a malformed row with its line number",
                "Open a buffered reader with UTF-8 in try-with-resources. Track line numbers and validate each row. Propagate or wrap IOException with context; never return a successful partial roster after a read failure. Test missing files, empty files and malformed rows."))),
        new Subject("CS202", "Data Structures and Algorithms", List.of(
            new Topic("Binary search", "Binary search halves the search interval in a sorted array",
                "Binary search works correctly on an arbitrary unsorted array",
                List.of("Binary search examines every element in the worst case", "The array may be unsorted if the target occurs once", "The midpoint must always be the first element"),
                "return the index of a target in a sorted integer array, or -1 when absent",
                "Use low=0 and high=length-1. While low<=high, choose mid=low+(high-low)/2. Move a bound beyond mid after a comparison. This is O(log n) time and O(1) space. Test empty input, first/last elements and absent values."),
            new Topic("Stacks and queues", "A queue serves elements in first-in-first-out order",
                "A stack removes the earliest item that was inserted",
                List.of("A stack and a queue always remove the same item", "A FIFO queue removes its newest item first", "A queue must sort requests by student name before removal"),
                "simulate a help-desk queue with enqueue, serve-next and an explicit empty-queue result",
                "Use ArrayDeque with addLast and pollFirst. After adding Ali then Sara, serving returns Ali, then Sara, then an empty result. Each operation is amortized O(1). Contrast with addLast/pollLast for a LIFO stack."),
            new Topic("Hash tables", "Collisions require a strategy such as chaining or probing",
                "Different keys can never have the same hash code",
                List.of("A hash code uniquely identifies every key", "A collision means one key must be discarded", "A hash table always traverses keys in sorted order"),
                "count normalized word frequencies with a map and explain how hash collisions preserve distinct words",
                "Normalize case consistently, split into words and merge each count with Integer::sum. For [Lab,lab,test], counts are lab=2 and test=1. Hash equality alone is insufficient: equality checks distinguish colliding keys. Expected insertion is O(1); test empty input and repeated words."),
            new Topic("Graph traversal", "Breadth-first search finds shortest paths in an unweighted graph",
                "Depth-first search always finds the fewest-edge path in an unweighted graph",
                List.of("BFS uses a LIFO stack to guarantee shortest paths", "Visited vertices should be marked only after all paths are explored", "BFS solves every weighted shortest-path problem without modification"),
                "find a fewest-edge route between two campus buildings in an unweighted adjacency-list graph",
                "Use a FIFO queue. Mark a vertex visited when enqueuing it and store its predecessor. Reconstruct the route backward from the destination. Complexity is O(V+E). Handle source=destination, disconnected buildings and cycles without revisiting vertices.")))
    );
    private DemoCurriculum() { }
}
