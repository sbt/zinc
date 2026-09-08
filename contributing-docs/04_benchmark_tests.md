Benchmarking Zinc
-----------------

To run JMH benchmarks, run the sbt task `runBenchmarks`. By default,
it will run a benchmark for Shapeless, but all benchmarks are welcome to be run
on the Scala standard library and other well-known projects in the community,
like Akka.

If you add a new benchmark, make sure that you define the new benchmarking repo
in [BenchmarkProjects.scala](https://github.com/sbt/zinc/blob/d532d15139f9f6e8346c8ffb649e564b25d7e897/internal/zinc-benchmarks/src/main/scala/xsbt/BenchmarkProjects.scala)
and that you define how the benchmarks should be run (have a look at the
[Shapeless JMH definition](https://github.com/sbt/zinc/blob/d532d15139f9f6e8346c8ffb649e564b25d7e897/internal/zinc-benchmarks/src/main/scala/xsbt/ShapelessBenchmark.scala)).
Finally, add your project to the [GlobalBenchmarkSetup.scala](https://github.com/sbt/zinc/blob/d532d15139f9f6e8346c8ffb649e564b25d7e897/internal/zinc-benchmarks/src/main/scala/xsbt/GlobalBenchmarkSetup.scala).
