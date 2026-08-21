import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.earendil.todonotes.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TodoNotes"
    ) {
        App()
    }
}
