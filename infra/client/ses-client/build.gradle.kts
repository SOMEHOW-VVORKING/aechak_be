plugins { id("aechak.spring-library") }
dependencies {
    implementation(project(":application"))
    implementation(platform(libs.spring.cloud.aws.bom))
    implementation(libs.spring.cloud.aws.starter.ses)
    implementation(libs.slf4j.api)
}
