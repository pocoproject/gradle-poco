plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":kotlinDsl"))
    implementation("com.google.code.gson:gson:2.7")
}

gradlePlugin {
    plugins {
        register("availableJavaInstallations") {
            id = "gradlebuild.available-java-installations"
            implementationClass = "org.gradle.gradlebuild.java.AvailableJavaInstallationsPlugin"
        }
        register("dependenciesMetadataRules") {
            id = "gradlebuild.dependencies-metadata-rules"
            implementationClass = "org.gradle.gradlebuild.dependencies.DependenciesMetadataRulesPlugin"
        }
    }
}
