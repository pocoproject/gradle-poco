import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":core"))
    compile(library("jcip"))

    integTestCompile(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
    from(":logging")
}
