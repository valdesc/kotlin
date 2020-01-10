
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(kotlinStdlib())
    testCompile(project(":compiler:cli"))
    testCompile(project(":kotlin-test:kotlin-test-jvm"))
    testCompile(projectTests(":compiler:tests-common"))
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

testsJar {}

projectTest(parallel = true) {
    dependsOn(":kotlin-stdlib-js-ir:generateFullRuntimeKLib")
}
