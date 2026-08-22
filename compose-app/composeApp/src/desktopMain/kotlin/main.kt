import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.earendil.todonotes.App
import com.earendil.todonotes.AppContainer
import com.earendil.todonotes.notification.NoopAlarmScheduler

fun main() = application {
    val container = AppContainer(alarmScheduler = NoopAlarmScheduler())
    Window(
        onCloseRequest = {
            container.close()
            exitApplication()
        },
        title = "TodoNotes"
    ) {
        App(container)
    }
}
