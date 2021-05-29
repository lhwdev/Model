import com.lhwdev.build.*


plugins {
	kotlin("multiplatform")
	
	id("common-plugin")
}

kotlin {
	library()
	
	dependencies {
		implementation(project(":core"))
	}
}
