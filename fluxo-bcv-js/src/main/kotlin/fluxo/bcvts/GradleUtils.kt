package fluxo.bcvts

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion


internal inline fun <reified T : Task> Project.task(
    name: String,
    configuration: Action<T>,
): TaskProvider<T> = tasks.register(name, T::class.java, configuration)


/**
 * Compatibility method for Gradle 8.6+ and older.
 */
@Suppress("UnstableApiUsage")
internal fun <T : Named> NamedDomainObjectCollection<T>.namedCompat(
    nameFilter: Spec<String>,
): NamedDomainObjectCollection<T> {
    if (HAS_NEW_NAMED_METHOD) {
        // Since Gradle 8.6, a new method `named(Spec<String>)` is available.
        // It provides lazy name-based filtering of tasks
        // without triggering the creation of the tasks,
        // even when the task was not part of the build execution.
        // https://docs.gradle.org/8.6/release-notes.html#lazy-name-based-filtering-of-tasks
        // https://docs.gradle.org/8.6/javadoc/org/gradle/api/NamedDomainObjectSet.html#named-org.gradle.api.specs.Spec-
        try {
            return named(nameFilter)
        } catch (_: NoSuchMethodError) {
        }
    }

    // Fallback for older Gradle versions
    return matching { nameFilter.isSatisfiedBy(it.name) }
}

/**
 * Compatibility method for Gradle 8.6+ and older.
 */
internal fun <T : Task, R : T> TaskCollection<T>.namedCompat(
    nameFilter: Spec<String>,
): TaskCollection<R> {
    if (HAS_NEW_NAMED_METHOD) {
        // Since Gradle 8.6, a new method `named(Spec<String>)` is available.
        // It provides lazy name-based filtering of tasks
        // without triggering the creation of the tasks,
        // even when the task was not part of the build execution.
        // https://docs.gradle.org/8.6/release-notes.html#lazy-name-based-filtering-of-tasks
        // https://docs.gradle.org/8.6/javadoc/org/gradle/api/NamedDomainObjectSet.html#named-org.gradle.api.specs.Spec-
        try {
            return uncheckedCast(named(nameFilter))
        } catch (_: NoSuchMethodError) {
        }
    }

    // Fallback for older Gradle versions
    return uncheckedCast(matching { nameFilter.isSatisfiedBy(it.name) })
}

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
private inline fun <T> uncheckedCast(obj: Any?): T = obj as T

private val HAS_NEW_NAMED_METHOD = GradleVersion.current() >= GradleVersion.version("8.6")


internal fun TaskContainer.maybeRegister(
    name: String,
    configure: (Task.() -> Unit)? = null,
): TaskProvider<Task> {
    val provider = when {
        has(name) -> named(name)
        else -> register(name)
    }
    configure?.let { provider.configure(it) }
    return provider
}

internal fun NamedDomainObjectCollection<*>.has(name: String): Boolean = name in names
