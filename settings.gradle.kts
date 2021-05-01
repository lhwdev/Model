rootProject.apply {
	name = "Model"
}


includeBuild("includeBuild")

include(":compiler-plugin")
include(":core")
