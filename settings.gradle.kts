pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MobileAppStarter"
include(":app")

include(":platform:realtime")
include(":platform:networking")
include(":platform:diagnostics")
include(":platform:storage")
include(":integrations:bluetooth")
include(":integrations:camera")
include(":integrations:location")
include(":integrations:nfc")
include(":integrations:usb")
include(":integrations:iot")
