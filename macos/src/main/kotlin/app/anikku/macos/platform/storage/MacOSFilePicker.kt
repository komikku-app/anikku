package app.anikku.macos.platform.storage

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * macOS file picker utility.
 *
 * Replaces Android `Intent.ACTION_OPEN_DOCUMENT` / SAF with
 * AWT FileDialog for native macOS look and feel.
 *
 * ## Usage
 *
 * ```kotlin
 * val picker = MacOSFilePicker()
 *
 * // Open a single file
 * val file = picker.openFile(title = "Select Backup", extensions = listOf("tachibk", "json"))
 *
 * // Open a directory
 * val dir = picker.openDirectory(title = "Choose Library Location")
 *
 * // Save a file
 * val output = picker.saveFile(title = "Save Backup", defaultName = "backup.tachibk")
 * ```
 */
class MacOSFilePicker {

    companion object {
        private const val PREFERRED_WIDTH = 600
        private const val PREFERRED_HEIGHT = 500
    }

    /** A parent frame used for dialog modality. Lazily created. */
    private var parentFrame: Frame? = null

    init {
        // Create a hidden parent frame on the EDT
        javax.swing.SwingUtilities.invokeAndWait {
            parentFrame = Frame().apply {
                isVisible = false
                setSize(PREFERRED_WIDTH, PREFERRED_HEIGHT)
            }
        }
    }

    /**
     * Open a file picker dialog for selecting a single file.
     *
     * @param title Dialog title.
     * @param extensions List of file extensions to filter (without dots).
     * @param currentDir Optional starting directory.
     * @return Selected file, or null if cancelled.
     */
    fun openFile(
        title: String = "Open File",
        extensions: List<String> = emptyList(),
        currentDir: File? = null,
    ): File? {
        return openFileAWT(title, extensions, currentDir)
    }

    /**
     * Open a file picker dialog for selecting multiple files.
     *
     * @param title Dialog title.
     * @param extensions List of file extensions to filter.
     * @param currentDir Optional starting directory.
     * @return List of selected files, or empty if cancelled.
     */
    fun openFiles(
        title: String = "Open Files",
        extensions: List<String> = emptyList(),
        currentDir: File? = null,
    ): List<File> {
        return openFileAWT(title, extensions, currentDir)?.let { listOf(it) } ?: emptyList()
    }

    /**
     * Open a directory picker dialog.
     *
     * @param title Dialog title.
     * @param currentDir Optional starting directory.
     * @return Selected directory, or null if cancelled.
     */
    fun openDirectory(
        title: String = "Select Directory",
        currentDir: File? = null,
    ): File? {
        return openDirectoryAWT(title, currentDir)
    }

    /**
     * Open a save file dialog.
     *
     * @param title Dialog title.
     * @param defaultName Default filename.
     * @param extensions List of file extensions to filter.
     * @param currentDir Optional starting directory.
     * @return Selected file path, or null if cancelled.
     */
    fun saveFile(
        title: String = "Save File",
        defaultName: String = "untitled",
        extensions: List<String> = emptyList(),
        currentDir: File? = null,
    ): File? {
        return saveFileAWT(title, defaultName, extensions, currentDir)
    }

    /**
     * Clean up the hidden parent frame.
     */
    fun dispose() {
        parentFrame?.dispose()
        parentFrame = null
    }

    // -------------------------------------------------------------------------
    // AWT FileDialog implementations (macOS native look & feel)
    // -------------------------------------------------------------------------

    private fun openFileAWT(
        title: String,
        extensions: List<String>,
        currentDir: File?,
    ): File? {
        return runOnEDT {
            val dialog = FileDialog(parentFrame, title, FileDialog.LOAD).apply {
                isMultipleMode = false
                if (currentDir != null) directory = currentDir.absolutePath
                if (extensions.isNotEmpty()) {
                    file = "*.${extensions.first()}"
                }
                isVisible = true
            }
            dialog.file?.let { File(dialog.directory, it) }
        }
    }

    private fun openDirectoryAWT(title: String, currentDir: File?): File? {
        return runOnEDT {
            // AWT FileDialog doesn't support directory-only selection.
            // Fall back to Swing JFileChooser for this.
            openDirectorySwing(title, currentDir)
        }
    }

    private fun saveFileAWT(
        title: String,
        defaultName: String,
        extensions: List<String>,
        currentDir: File?,
    ): File? {
        return runOnEDT {
            val dialog = FileDialog(parentFrame, title, FileDialog.SAVE).apply {
                file = defaultName
                if (currentDir != null) directory = currentDir.absolutePath
                if (extensions.isNotEmpty()) {
                    file = "*.${extensions.first()}"
                }
                isVisible = true
            }
            dialog.file?.let { File(dialog.directory, it) }
        }
    }

    // -------------------------------------------------------------------------
    // Swing JFileChooser implementations (for multi-file + directory)
    // -------------------------------------------------------------------------

    private fun openFileSwing(
        title: String,
        extensions: List<String>,
        currentDir: File?,
    ): File? {
        return runOnEDT {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = false
                currentDir?.let { currentDirectory = it }
                if (extensions.isNotEmpty()) {
                    fileFilter = FileNameExtensionFilter(
                        extensions.joinToString(", ") { ".$it" },
                        *extensions.toTypedArray(),
                    )
                }
            }
            if (chooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else null
        }
    }

    private fun openFilesSwing(
        title: String,
        extensions: List<String>,
        currentDir: File?,
    ): List<File> {
        return runOnEDT {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = true
                currentDir?.let { currentDirectory = it }
                if (extensions.isNotEmpty()) {
                    fileFilter = FileNameExtensionFilter(
                        extensions.joinToString(", ") { ".$it" },
                        *extensions.toTypedArray(),
                    )
                }
            }
            if (chooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFiles?.toList() ?: emptyList()
            } else emptyList()
        }
    }

    private fun openDirectorySwing(title: String, currentDir: File?): File? {
        return runOnEDT {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isMultiSelectionEnabled = false
                currentDir?.let { currentDirectory = it }
            }
            if (chooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else null
        }
    }

    private fun saveFileSwing(
        title: String,
        defaultName: String,
        extensions: List<String>,
        currentDir: File?,
    ): File? {
        return runOnEDT {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                selectedFile = File(defaultName)
                currentDir?.let { currentDirectory = it }
                if (extensions.isNotEmpty()) {
                    fileFilter = FileNameExtensionFilter(
                        extensions.joinToString(", ") { ".$it" },
                        *extensions.toTypedArray(),
                    )
                }
            }
            if (chooser.showSaveDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else null
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Run a block on the AWT Event Dispatch Thread.
     * Uses java.awt.EventQueue.invokeAndWait directly, avoiding
     * the overhead of Kotlin coroutines.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> runOnEDT(block: () -> T): T {
        if (java.awt.EventQueue.isDispatchThread()) {
            return block()
        }
        val ref = arrayOfNulls<Any>(1)
        java.awt.EventQueue.invokeAndWait {
            ref[0] = block()
        }
        return ref[0] as T
    }
}
