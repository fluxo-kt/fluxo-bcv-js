package fluxo.bcvjs

/**
 * Copy of [kotlinx.validation.DirConfig].
 *
 * @see kotlinx.validation.DirConfig
 */
internal enum class DirConfig {
    /**
     * `api` directory for .api files.
     * Used in single target projects.
     */
    COMMON,

    /**
     * Target-based directory, used in multitarget setups.
     * E.g. for the project with targets JVM and Android,
     * the resulting paths will be
     * `/api/jvm/project.api` and `/api/android/project.api`.
     */
    TARGET_DIR,
}
