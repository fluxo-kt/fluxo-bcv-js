package fluxo.bcvts

/**
 * A customized copy of the [kotlinx.validation.DirConfig].
 *
 * @see kotlinx.validation.DirConfig
 */
@Suppress("ClassName")
internal interface DirConfig {
    /**
     * `api` directory for .api files.
     * Used in single target projects.
     */
    class COMMON(val bcvTargetName: String?) : DirConfig

    /**
     * Target-based directory, used in multitarget setups.
     * For example, for the project with targets JVM and Android,
     * the resulting paths will be
     * `/api/jvm/project.api` and `/api/android/project.api`.
     */
    object TARGET_DIR : DirConfig
}
