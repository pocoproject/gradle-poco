import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    compile(project(":languageNative"))
    compile(project(":testingNative"))
    compile(project(":toolingApi"))
    // To pick up various builders (which should live somewhere else)
    compile(project(":ide"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":platformNative")
}
