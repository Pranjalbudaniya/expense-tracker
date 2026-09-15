pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "ExpenseTracker"

include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:navigation")
include(":core:database")
include(":core:data")
include(":core:di")
include(":core:preferences")
include(":feature:home")
include(":feature:addtransaction")
include(":feature:transactions")
include(":feature:settings")
include(":feature:budgets")
include(":feature:accounts")
include(":feature:categories")
include(":feature:recurring")
include(":feature:statistics")
include(":feature:export")
include(":feature:security")
include(":core:notifications")
include(":feature:notifications")
include(":feature:backup")
