- Support for Scala 2.7, 2.8 and 2.9 has been dropped. [#86][86]

  [86]: https://github.com/sbt/zinc/pull/86
  
- sbt.internal.inc.ClassfileManager has been renamed to sbt.internal.inc.ClassFileManager for consistency. [#87][87]

  [87]: https://github.com/sbt/zinc/pull/86

- Same-project changes to a package scope now invalidate sources that could resolve a name
  differently through a wildcard import or the enclosing package — the same-project half of
  [#226][226] (which stays open: upstream/library additions and empty-package removal are not
  covered). A non-implicit top-level addition invalidates users of that simple name; adding or
  changing a package-scoped implicit invalidates every source that can see the package (no name
  needed); removing a package that held a definition invalidates users of its path. Disable with
  `incOptions := incOptions.value.withInvalidateScopeChanges(false)`.

  [226]: https://github.com/sbt/zinc/issues/226
