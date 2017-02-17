### CLA

To contribute to Zinc, you or your employer must sign Lightbend Contributor License Agreement <https://www.lightbend.com/contribute/cla>.

If you add a new file, run `createHeaders` task from sbt shell to put the copyright notice.

### To build with other module source

```
$ sbt -Dsbtio.path=../io -Dsbtutil.path=../util -Dsbtlm.path=../librarymanagement
```
