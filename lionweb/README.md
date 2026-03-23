# Module lionweb

Integration module that converts between Kolasu ASTs and [LionWeb](https://lionweb.io) models.

## Core API

| Class | Purpose |
|---|---|
| `LionWebModelConverter` | Bidirectional conversion: `exportModelToLionWeb` / `importModelFromLionWeb` |
| `LionWebLanguageConverter` | Converts a `KolasuLanguage` to a LionWeb `Language` |
| `StructuralLionWebNodeIdProvider` | Generates stable structural node IDs (default, wraps `CachingNodeIDProvider`) |

## Performance benchmarks

The module ships JMH microbenchmarks in `src/jmh/kotlin/`. They cover the three
hotspots identified via profiling:

| Benchmark class | What it measures |
|---|---|
| `LionWebTreeWalkerBenchmark` | `thisAndAllDescendants` and `thisAndAllDescendantsLeavesFirst` on small (16-node) and large (301-node) trees |
| `ImportModelBenchmark` | `importModelFromLionWeb` on 5-node and 201-node trees |
| `ExportModelBenchmark` | `exportModelToLionWeb` — exercises the `CachingNodeIDProvider` path |

### Running all benchmarks

```bash
./gradlew :lionweb:jmh
```

Results are printed to the console and written to
`lionweb/build/results/jmh/results.txt`.

### Running a single benchmark class

```bash
./gradlew :lionweb:jmh --include "ImportModelBenchmark"
```

Or pass JMH options directly via the `jmhArgs` system property:

```bash
./gradlew :lionweb:jmh -PjmhArgs="-i 3 -wi 2 -f 1 -t 1 -rf json"
```

### Running with memory-allocation profiling (GCProfiler)

Each benchmark class has a `main()` entry point that adds `GCProfiler` automatically.
Build the fat jar and run it:

```bash
./gradlew :lionweb:jmhJar
java -jar lionweb/build/libs/lionweb-jmh.jar ImportModelBenchmark
java -jar lionweb/build/libs/lionweb-jmh.jar LionWebTreeWalkerBenchmark
java -jar lionweb/build/libs/lionweb-jmh.jar ExportModelBenchmark
```

GCProfiler output includes `·gc.alloc.rate.norm` (bytes allocated per operation),
which is the key metric for judging whether a change reduces GC pressure.

### Benchmark configuration

All benchmark classes use:

```
@BenchmarkMode(Mode.AverageTime)   // average time per operation
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1s)
@Measurement(iterations = 5, time = 1s)
@Fork(1)
```

State is scoped to `Scope.Thread`. The `@Setup(Level.Invocation)` method clears
`LionWebModelConverter.nodesMapping` before each invocation so imports start
fresh, while the internal JIT-friendly caches (`kClassCache`, `factoryCache`)
stay warm across invocations — matching production behaviour.

### Adding a new benchmark

1. Create a Kotlin file under `src/jmh/kotlin/com/strumenta/kolasu/lionweb/`.
2. Annotate the class with `@State`, `@BenchmarkMode`, `@Warmup`, `@Measurement`,
   `@Fork`.
3. Annotate each measured method with `@Benchmark`; consume results via `Blackhole`
   or by returning them.
4. Add a `companion object { @JvmStatic fun main(...) }` that builds `OptionsBuilder`
   and adds `.addProfiler(GCProfiler::class.java)`.
