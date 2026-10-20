// Standalone verification build: do not configure the Android app or its SDKs.
// Keep this Kotlin version aligned with gradle/libs.versions.toml.
plugins {
    kotlin("jvm") version "2.3.20"
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("../../app/src/main/java/com/example/openruncoach/domain")
        }
        test {
            kotlin.srcDir("../../app/src/test/java/com/example/openruncoach/domain")
            // These suites import data adapters/Room mapping helpers.
            // They remain in the normal app suite, not this pure JVM check.
            kotlin.exclude("metrics/RunMetricsEnrichmentTest.kt")
            kotlin.exclude("workout/WorkoutResponseEvaluatorTest.kt")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    failOnNoDiscoveredTests.set(true)
    testLogging {
        events("failed", "skipped")
    }
}
