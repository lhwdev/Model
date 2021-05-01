import com.lhwdev.build.*


plugins {
	kotlin("jvm")
	
	id("common-plugin")
}

kotlin {
	setup()
}

tasks.named<Test>("test") {
	outputs.upToDateWhen { false }
	useJUnit()
}


dependencies {
	compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
	compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$kotlinVersion")
	
	compileOnly(gradleApi())
	
	// kotlin
	implementation(kotlin("reflect"))
	
	// test
	testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
	testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$kotlinVersion")
	testImplementation(kotlin("test"))
	testImplementation(kotlin("test-junit"))
	testImplementation("io.mockk:mockk:1.9.3")
	testRuntimeOnly("net.bytebuddy:byte-buddy:1.10.6")
	testImplementation("io.github.classgraph:classgraph:4.8.63")
	// testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.6")
}
