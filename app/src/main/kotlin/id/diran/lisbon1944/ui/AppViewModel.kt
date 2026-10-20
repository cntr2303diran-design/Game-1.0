package id.diran.lisbon1944.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.ScoreBreakdown
import id.diran.lisbon1944.session.ActionText
import id.diran.lisbon1944.session.GameSession
import id.diran.lisbon1944.session.Lang
import id.diran.lisbon1944.session.LogKind
import id.diran.lisbon1944.session.LogLine
import id.diran.lisbon1944.session.LogText
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.session.StepResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Screen { SPLASH, MENU, SETUP, GAME, LOAD, SETTINGS, DOSSIER, END }

/** Snapshot yang dirender layar permainan. Hanya berisi PlayerView — tidak pernah GameState. */
data class GameUi(
    val view: PlayerView,
    val legal: List<Action>,
    val needsPrivacy: Boolean,
    val waitingOn: PlayerId?,
    val isBotTurn: Boolean,
    val isOver: Boolean,
    val humans: Int,
    val log: List<LogLine>,
    val lastEvents: List<GameEvent>,
    val scores: List<ScoreBreakdown>?,
    val playerNames: Map<Int, String>,
    val message: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SaveStore(app)
    private val mutex = Mutex()

    var screen by mutableStateOf(Screen.SPLASH)
        private set
    var content by mutableStateOf<GameContent?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var ui by mutableStateOf<GameUi?>(null)
        private set
    var settings by mutableStateOf(store.loadSettings())
        private set
    var slots by mutableStateOf(store.slots())
        private set
    var busy by mutableStateOf(false)
        private set
    var endSeen by mutableStateOf(false)
        private set

    val lang: Lang = if (Locale.getDefault().language == "en") Lang.EN else Lang.ID

    private var engine: GameEngine? = null
    private var bots: Bots? = null
    private var session: GameSession? = null
    private var slot: Int = 1
    private var botJob: Job? = null
    private var fastForward = false

    val names: Names? get() = content?.let { Names(it, lang) }

    init {
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) {
                    val c = ContentLoader.loadDefault()
                    engine = GameEngine(c)
                    bots = Bots(c)
                    c
                }
            }.onSuccess { content = it }.onFailure { loadError = it.message ?: it.toString() }
            val wait = 1600 - (System.currentTimeMillis() - started)
            if (wait > 0) delay(wait)
            if (screen == Screen.SPLASH) screen = Screen.MENU
        }
    }

    fun go(to: Screen) {
        if (to == Screen.LOAD || to == Screen.MENU) slots = store.slots()
        screen = to
    }

    fun updateSettings(s: Settings) {
        settings = s
        store.saveSettings(s)
    }

    /** Slot simpanan terbaru yang belum selesai (untuk tombol Lanjutkan). */
    val continueSlot: Int?
        get() = slots.filterNotNull().filter { !it.meta.finished }.maxByOrNull { it.savedAt }?.slot

    fun newGame(config: GameConfig, seed: Long) {
        val e = engine ?: return
        val b = bots ?: return
        viewModelScope.launch {
            busy = true
            val s = withContext(Dispatchers.Default) { GameSession.newGame(e, b, config, seed) }
            session = s
            endSeen = false
            slot = store.freeSlot()
            busy = false
            publish(emptyList())
            screen = Screen.GAME
            autosave()
            pump()
        }
    }

    fun loadSlot(n: Int) {
        val e = engine ?: return
        val b = bots ?: return
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { GameSession.load(e, b, store.read(n) ?: error("kosong")) }
            }
            busy = false
            result.onSuccess {
                session = it
                endSeen = it.isOver
                slot = n
                publish(emptyList())
                screen = if (it.isOver) Screen.END else Screen.GAME
                pump()
            }.onFailure {
                loadError = it.message
                slots = store.slots()
            }
        }
    }

    fun deleteSlot(n: Int) {
        store.delete(n)
        slots = store.slots()
    }

    fun dismissError() {
        loadError = null
    }

    fun reveal() {
        val s = session ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) { mutex.withLock { s.reveal() } }
            publish(emptyList())
        }
    }

    fun act(action: Action) {
        val s = session ?: return
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) { mutex.withLock { s.act(action) } }
            when (r) {
                is StepResult.Ok -> {
                    publish(r.events)
                    autosave()
                    pump()
                }
                is StepResult.Rejected -> publish(emptyList(), message = r.reason.message)
                StepResult.NotYourTurn -> publish(emptyList())
            }
        }
    }

    /** Lewati animasi bot sampai giliran manusia berikutnya. */
    fun skipBots() {
        fastForward = true
    }

    fun clearMessage() {
        ui = ui?.copy(message = null)
    }

    fun quitToMenu() {
        botJob?.cancel()
        viewModelScope.launch {
            autosave()
            session = null
            ui = null
            go(Screen.MENU)
        }
    }

    fun showEnd() {
        endSeen = true
        screen = Screen.END
    }

    private fun pump() {
        if (botJob?.isActive == true) return
        botJob = viewModelScope.launch {
            try {
                var steps = 0
                while (true) {
                    val s = session ?: break
                    val botTurn = mutex.withLock { s.isBotTurn }
                    if (!botTurn) break
                    val d = if (fastForward) 0L else settings.speed.delayMs
                    if (d > 0) delay(d)
                    val r = withContext(Dispatchers.Default) { mutex.withLock { s.stepBot() } }
                    steps++
                    val events = (r as? StepResult.Ok)?.events.orEmpty()
                    if (d > 0 || steps % 25 == 0) publish(events)
                    if (steps % 40 == 0) autosave()
                }
                fastForward = false
                publish(emptyList())
                autosave()
                if (session?.isOver == true && !endSeen) {
                    endSeen = true
                    screen = Screen.END
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Jangan membeku diam-diam: tampilkan kesalahan; permainan tetap tersimpan di slot terakhir.
                loadError = "${e::class.simpleName}: ${e.message}"
            }
        }
    }

    private suspend fun publish(events: List<GameEvent>, message: String? = null) {
        val s = session ?: return
        val c = content ?: return
        val e = engine ?: return
        val snap = withContext(Dispatchers.Default) {
            mutex.withLock {
                val view = s.view()
                val logText = LogText(Names(c, lang))
                val names = logText.playerNames(view)
                val log = logText.lines(view, limit = 160).filter { settings.showSecretsInLog || it.kind != LogKind.SECRET }
                GameUi(
                    view = view,
                    legal = s.legal(),
                    needsPrivacy = s.needsPrivacy,
                    waitingOn = s.decider,
                    isBotTurn = s.isBotTurn,
                    isOver = s.isOver,
                    humans = s.humans.size,
                    log = log,
                    lastEvents = events,
                    scores = if (s.isOver) e.computeScores(s.state) else null,
                    playerNames = names,
                    message = message,
                )
            }
        }
        ui = snap
    }

    private suspend fun autosave() {
        val s = session ?: return
        val n = slot
        val (text, meta) = withContext(Dispatchers.Default) { mutex.withLock { s.save() to s.meta() } }
        withContext(Dispatchers.IO) { runCatching { store.write(n, text, meta) } }
        slots = store.slots()
    }

    /** Pesan pelanggaran aturan bila aksi tidak sah (dipakai penyusun Tawaran Dagang), null bila sah. */
    fun check(action: Action): String? = session?.check(action)?.message

    fun actionText(): ActionText? {
        val n = names ?: return null
        val map = ui?.playerNames.orEmpty()
        return ActionText(n) { map[it.value] ?: "P${it.value + 1}" }
    }
}
