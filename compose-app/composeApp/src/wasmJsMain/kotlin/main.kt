import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.earendil.todonotes.App
import com.earendil.todonotes.AppContainer
import com.earendil.todonotes.notification.NoopAlarmScheduler

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = AppContainer(alarmScheduler = NoopAlarmScheduler())
    container.setupAppLifecycle()
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "TodoNotes") {
        App(container)
    }
}
