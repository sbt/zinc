Zinc
====

Zinc is an incremental compilation module of sbt with the sbt-agnostic API.

Zinc has been spawned with this command executed in sbt's repo:

```
$ git filter-branch --index-filter 'git rm --cached -qr -- . && git reset -q $GIT_COMMIT -- compile interface util/classfile util/classpath util/datatype util/relation' --prune-empty
```

Status
======

The zinc project that lives under "sbt" organization is a successor to [`typesafehub/zinc`](https://github.com/typesafehub/zinc). The intent is to polish this project and retire zinc living under typesafehub. Check [#80](https://github.com/sbt/zinc/issues/80) for more information.
