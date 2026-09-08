Contributor's guide
===================

Zinc is a piece of software used by all Scala developers all around the globe.
Contributing to it has a far-reaching impact in all these Scala developers,
and the Zinc team tries to make it a fun and motivating experience.

Start with an issue
-------------------

To contribute to Zinc, start a conversation before creating a pull request. This can be in a new [Issue](https://github.com/sbt/zinc/issues), continuing the conversation in an existing issue, [ Discussion](https://github.com/sbt/zinc/discussions), or any other communication channel being used by the project. This gives maintainers and other contributors a chance to work with you on your idea at an earlier stage to make sure it is an acceptable contribution.

<a id="issues"></a>
Reporting Issues
----------------

## Reporting bugs to Zinc

Effective bug reports are more likely to be fixed. These guidelines explain how to write such reports.

Please open a GitHub issue when you are 90% sure it's an actual bug.

### What to report

The developers need three things from you: **steps**, **problems**, and **expectations**.

The most important thing to remember about bug reporting is to clearly distinguish facts and opinions.

### Steps

What we need first is **the exact steps to reproduce your problems on our computers**. This is called *reproduction steps*, which is often shortened to "repro steps" or "steps." Describe your method of running sbt. Provide `build.sbt` that caused the problem and the version of sbt or Scala that was used. Provide sample Scala code if it's to do with incremental compilation. If possible, minimize the problem to reduce non-essential factors.

Repro steps are the most important part of a bug report. If we cannot reproduce the problem in one way or the other, the problem can't be fixed. Telling us the error messages is not enough.

### Problems

Next, describe the problems, or what *you think* is the problem. It might be "obvious" to you that it's a problem, but it could actually be an intentional behavior for some backward compatibility etc. For compilation errors, include the stack trace. The more raw info the better.

### Expectations

Same as the problems. Describe what *you think* should've happened.

### Notes

Add any optional notes section to describe your analysis.

### Subject

The subject of the bug report doesn't matter. A more descriptive subject is certainly better, but a good subject really depends on the analysis of the problem, so don't worry too much about it. "Undercompilation after changing Java enum" is good enough.

### Formatting

If possible, please format code or console outputs.

On GitHub it's:

    ```scala
    name := "foo"
    ```

On StackOverflow, it's:

```
<!-- language: lang-scala -->

    name := "foo"
```

Here's a simple sample case: [#830](https://github.com/sbt/zinc/issues/830).
Finally, thank you for taking the time to report a problem.

Reading up
----------

If this is your first time contributing to Zinc, take some time to get familiar
with Zinc. To get you started as soon as possible, we have written a series of
guides that explain the underlying concepts of Zinc and how incremental
compilation works in 1.0.

Guides:

* [Understanding Incremental Recompilation](https://www.scala-sbt.org/1.x/docs/Understanding-Recompilation.html).
* [Scala Lang blog post on Zinc release](https://www.scala-lang.org/blog/2017/11/03/zinc-blog-1.0.html).
* All the issues and PRs labelled as `docs` will help you understand different
  aspects of Zinc. They both count with concrete information that are helpful
  to understand tradeoffs and implementation details.

If you find this information outdated, open a pull request, issue, or discussion.

Patching the core (send pull requests)
--------------------------------------

This section describes how you can create Pull Requests (PRs) and describes coding standards we use when implementing them.

<a id="important"></a>
### **Important**:  ⚠️ Pull request must be tested with GitHub Actions or human-in-the-loop

Given the wide user base and the long history, not all issues are valid or relevant.

- [ ] Before working on a pull request, please confirm with a Maintainer that a contribution is wanted for the issue.
- [ ] Before working on a pull request, please confirm that **you can reproduce the reported problem** using GitHub Actions or your computer.
- [ ] After making the code change, please confirm that **your change compiles, and has fixed the problem**.

We do not always have the bandwidth to play the QA role. To must minimize the review burden, Maintainers might close a PR if it fails to pass the CI in a few rounds.

If you can express the reproduction as a test that would be great, but often the problems require locally building Zinc and running test builds yourself. For local testing, post screenshots or screencast to demonstrate that the fix works at least on your machine.

### Compiling with sbt

```bash
sbt --client compile
```

<a id="genai"></a>
### AI assisted contributions

See [LLM_POLICY.md](./LLM_POLICY.md).

<a id="getting-started"></a>
### Getting started

1. Create a [fork](https://docs.github.com/en/github/getting-started-with-github/fork-a-repo) of the repository.
2. Go to the Actions tab, and enable the workflows. **This will let you run the CI tests on your forked repository**.
3. [Clone](https://docs.github.com/en/github/creating-cloning-and-archiving-repositories/cloning-a-repository) the forked repository to create a local copy.

### Branch to work against

Zinc uses two branches for development:
Use the **default** branch set on GitHub for bug fixes. For backports, use the latest stable branch.

- Development branch: `develop`
- Stable branch: `1.$MINOR.x`, where `$MINOR` is current minor version (e.g. `2.0.x` during 2.0.x series)

The `develop` branch represents the next minor update to sbt 2.x series.

### Pull Request guidelines

Before you submit a Pull Request (PR) from your forked repo, check that it meets these guidelines:

- Confirm that you can reproduce the problem prior to making the changes to the code.
- Include tests, either as scripted test or unit tests to your pull request, or screenshots from a manual test.
- Follow our project's [Commit message guideline](#commit).
- Follow our project's [Coding style and best practices][03].
- Sign the [Scala Contributor License Agreement](https://cla.scala-lang.org/sbt/zinc).
- Make sure your PR is small and focused on one change only - avoid adding unrelated changes, mixing adding features and refactoring. Keeping to that rule will make it easier to review your PR and will make it easier for core devs if they decide that your change should be cherry-picked to release it in a stable release of sbt.
- Maintainers will not merge a PR that regresses linting or does not pass CI tests (unless you have good justification that it a transient error or something that is being fixed in other PR).
- Maintainers will not merge a PR that breaks binary compatibility ("bincompat"). Run `mimaReportBinaryIssues` from the sbt shell.
- When merging PRs, Maintainer may use **Squash and Merge** which means then your PR will be merged as **one commit**, regardless of the number of commits in your PR. During the review cycle, you can keep a commit history for easier review.
- You can use any supported JDK version to run the tests, but the best is to check if it works for the oldest supported version (JDK 17 currently). In rare cases tests might fail with the oldest version when you use features that are available in newer JDK versions.
- Add an Apache header to all new files. Run `headerCreate` and sbt will put a copyright notice into it.

### General guidelines

See [Coding sylt and best practices](contributing-docs/01_coding_style.md).

### Testing

Zinc features a testing infrastructure encompassing multiple testing methodologies designed to ensure reliability and functionality across different integrations.

Incremental-compilation behavior is covered end to end by the scripted tests in `zinc/src/sbt-test`.

Zinc also has a JMH benchmark suite. This benchmark suite can benchmark
any project that runs on 2.12.x/2.11.x. The Zinc team uses it
to make sure that there's not a performance regression in the Zinc compiler phases.

- [Unit tests](contributing-docs/02_unit_tests.md)
- [Scripted tests](contributing-docs/03_scripted_tests.md)
- [Benchmark tests](contributing-docs/04_benchmark_tests.md)

<a id="commit"></a>
### Commit message guideline

Follow the following template:

```
[2.x] fix: Fix consoleProject not starting

**Problem**
consoleProject doesn't work. REPL doesn't even start.

**Solution**
I made some progress into consoleProject.
At least Scala 3.7 repl session will now start.

Generated-by: Claude Sonnet 4.5
```

1. (Optional) Subject should start with `[2.x]` for develop branch, and `[1.x]` for Zinc 1.x
2. Subject should start with `fix` (bug fix), `feat` (new feature), `refactor`, `test`, `ci`, or `deps`
3. Subject should use imperative mood, for example Fix foo, Add bar.
4. Body should include Problem section, which summarizes the current understanding of the issue.
5. Body should include Solution section, which summarizes your approach to fixing the issue.
6. Do not at-mention people in the commit message.
7. Include "Generated-by" tag for Gen-AI tools.

## Signing the CLA

Contributing to Zinc requires you or your employer to sign the
[Scala Contributor License Agreement](https://cla.scala-lang.org/sbt/zinc).
