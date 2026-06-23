# Contributing to Titan

Titan is a community project.
We welcome contributions from everyone.

- Environment: Java 21

## Code Style

- Titan generally uses JSpecify for nullness annotations. Avoid `Optional` unless it clearly improves the API, and prefer explicit nullness control.
- Add a space before control-flow parentheses such as `if` and `while`.
```java
class Foo {
    
    // OK
    void bar() {
        if (true) {
            // do something
        }
    }
    
    // NO
    void bar() {
        if(true) {
            // do something
        }
    }
}
```

- Avoid blocking API calls inside event-loop code. If blocking work is unavoidable, discuss it with a maintainer first.
- When a method has four or more parameters, place each parameter on its own line. Use the same style for three parameters when the parameter names are long.
```java

public void foo(
        String a, 
        String b, 
        String c
) {
    // do something
}
```

## Contribution Convention

Keep each contribution small enough to review with confidence.
If a change touches unrelated behavior, split it into separate commits or separate pull requests.

Use branch names that describe the work area and intent.
For example, `feature-monitor`, `feature-destination-backup`, or `release/0.7.2`.

Write commit messages as short imperative sentences.
Do not use prefixes such as `feat:`, `fix:`, or `test:`.
Good examples:

- `Add destination backup system`
- `Propagate connect failure during finish connect`
- `Document file buffer ownership`

Before opening a pull request, run the smallest meaningful verification command for the change.
For shared core behavior, prefer a broader test command.

Common validation commands:

```bash
./gradlew test
./gradlew :core:test
./gradlew :titan-stomp:test
./gradlew :titan-spring-client:test
cd titan-cli && go test ./...
```

Open pull requests as drafts while the design is still changing.
Mark the pull request ready only after the implementation, tests, and documentation are aligned.

Use the repository pull request template.
Keep the sections focused:

- `Motivation`: explain the problem or reason for the change.
- `Modification`: describe what changed in the code.
- `Result`: describe the outcome and include validation commands.

Do not include local editor files, generated caches, secrets, or unrelated formatting changes.
If a change requires follow-up work, mention it clearly in the pull request body instead of hiding it in the diff.
