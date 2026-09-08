AGENTS instructions
===================

The main developer documentation is [CONTRIBUTING.md](./CONTRIBUTING.md).

Compiling with sbt
------------------

```bash
sbt --client --color=false --supershell=false --batch compile
```

Pull request guideline
----------------------

- Follow the PR guidance in [CONTRIBUTING.md](./CONTRIBUTING.md).
- [ ] Before working on a pull request, please confirm that **you can reproduce the reported problem** using GitHub Actions or your computer.
- [ ] After making the code change, please confirm that **your change compiles, and has fixed the problem**.
- [ ] In the commit message, include "Generated-by" tag for Gen-AI tools.

Coding style
------------

```bash
sbt --client --color=false --supershell=false --batch scalafmtAll
```

- Follow [Coding style and best practices](contributing-docs/01_coding_style.md)
- Avoid inline comments!

Tests
-----

Always add tests. For changes with small scopes prefer HedgeHog for Scala.
For changes that require coordination with file changes and tasks, use scripted test.

- [contributing-docs/02_unit_tests.md](contributing-docs/02_unit_tests.md)
- [contributing-docs/03_scripted_tests.md](contributing-docs/03_scripted_tests.md)

For example, here's how to run "source-dependencies/abstract-override" scripted test:

```bash
sbt --color=false --supershell=false --client --batch scripted source-dependencies/abstract-override
```

Binary compatibility
--------------------

sbt MUST maintain backward binary compatibility across minor releases.
This means removing public method signature MUST be avoided.

Use mima to check:

```bash
sbt --client --color=false --supershell=false --batch mimaReportBinaryIssues
```

Copyright
---------

- NEVER reproduce copyrighted material.
