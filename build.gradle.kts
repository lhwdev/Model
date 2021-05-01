plugins {
	val kotlinVersion = "1.4.31"
	
	kotlin("multiplatform") version kotlinVersion apply false
	kotlin("jvm") version kotlinVersion apply false
	kotlin("plugin.serialization") version kotlinVersion apply false
}

allprojects {
	repositories {
		jcenter()
		google()
		mavenCentral()
		maven("https://dl.bintray.com/jetbrains/intellij-plugin-service")
		maven("https://plugins.gradle.org/m2/")
	}
}
