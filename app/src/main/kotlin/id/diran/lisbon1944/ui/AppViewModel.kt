package id.diran.lisbon1944.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.bot.deduction.DeductionInput
import id.diran.lisbon1944.bot.deduction.DeductionTracker
import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.ScoreBreakdown
import id.diran.lisbon1944.session.ActionText
import id.diran.lisbon1944.session.Cue
import id.diran.lisbon1944.session.GameSession
import id.diran.lisbon1944.session.Lang
import id.diran.lisbon1944.session.LogKind
import id.diran.lisbon1944.session.LogLine
import id.diran.lisbon1944.session.LogText
import id.diran.lisbon1944.session.Names
import id.diran.lisbon1944.session.Sfx
import id.diran.lisbon1944.session.StepResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Screen { SPLASH, MENU, SETUP, GAME, LOAD, SETTINGS, DOSSIER, END, ARCHIVE, HOW_TO }

/** Ringkasan panel "Kemungkinan Amplop" (DeductionTracker atas PlayerView pemain sendiri). */
data class Possibilities(
    val combos: Int,
    val envelopeProb: Map<CardId, Double>,
    val best: List<CardId>?,
    val bestProb: Double,
)

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
    val possibilities: Possibilities? = null,
    val gameId: String = "",
    val seed: Long = 0,
)

/** Tutorial: satu giliran terpandu melawan dua bot Mudah. */
data class TutorialState(val seen: Set<String> = emptySet(), val finished: Boolean = false, val dismissed: Boolean = false)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SaveStore(app)
    private val archiveStore = ArchiveStore(app)
    private val notesStore = NotesStore(app)
    private val mutex = Mutex()
    val sound = SoundBoard(app)
    private val haptics = Haptics(app)

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
    var introPending by mutableStateOf(false)
        private set
    var tutorial by mutableStateOf<TutorialState?>(null)
        private set
    var archive by mutableStateOf<List<ArchiveEntry>>(emptyList())
        private set

    /** Antrean isyarat visual (dadu, Front, cap, uang, kartu) — dimainkan CueLayer satu per satu. */
    val cues = mutableStateListOf<Cue>()

    val lang: Lang
        get() = when (settings.language) {
            AppLanguage.EN -> Lang.EN
            AppLanguage.ID -> Lang.ID
            AppLanguage.SYSTEM -> if (Locale.getDefault().language == "en") Lang.EN else Lang.ID
        }

    private var engine: GameEngine? = null
    private var bots: Bots? = null
    private var tracker: DeductionTracker? = null
    private var session: GameSession? = null
    private var slot: Int = 1
    private var botJob: Job? = null
    private var fastForward = false
    private var snapshotSeq = 0L
    private var shownSeq = 0L
    private var lastConfig: GameConfig? = null

    val names: Names? get() = content?.let { Names(it, lang) }

    init {
        haptics.enabled = settings.vibration
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) {
                    val c = ContentLoader.loadDefault()
                    engine = GameEngine(c)
                    bots = Bots(c)
                    tracker = DeductionTracker(c)
                    c
                }
            }.onSuccess { content = it }.onFailure { loadError = it.message ?: it.toString() }
            archive = withContext(Dispatchers.IO) { archiveStore.all() }
            val wait = 2600 - (System.currentTimeMillis() - started)
            if (wait > 0) delay(wait)
            if (screen == Screen.SPLASH) screen = Screen.MENU
        }
        viewModelScope.launch(Dispatchers.IO) {
            sound.prepare()
            sound.configure(settings)
        }
    }

    override fun onCleared() {
        sound.release()
    }

    fun skipSplash() {
        if (screen == Screen.SPLASH && content != null) screen = Screen.MENU
    }

    fun go(to: Screen) {
        if (to == Screen.LOAD || to == Screen.MENU) slots = store.slots()
        if (to == Screen.ARCHIVE) archive = archiveStore.all()
        screen = to
    }

    fun updateSettings(s: Settings) {
        settings = s
        store.saveSettings(s)
        haptics.enabled = s.vibration
        sound.configure(s)
    }

    fun clearArchive() {
        archiveStore.clear()
        archive = emptyList()
    }

    /** Slot simpanan terbaru yang belum selesai (untuk tombol Lanjutkan). */
    val continueSlot: Int?
        get() = slots.filterNotNull().filter { !it.meta.finished }.maxByOrNull { it.savedAt }?.slot

    fun newGame(config: GameConfig, seed: Long, intro: Boolean = true, asTutorial: Boolean = false) {
        val e = engine ?: return
        val b = bots ?: return
        viewModelScope.launch {
            busy = true
            val s = withContext(Dispatchers.Default) { GameSession.newGame(e, b, config, seed) }
            botJob?.cancel()
            session = s
            lastConfig = config
            endSeen = false
            cues.clear()
            tutorial = if (asTutorial) TutorialState() else null
            slot = store.freeSlot()
            busy = false
            introPending = intro && !settings.reduceMotion && settings.speed != Speed.INSTANT
            publish(emptyList())
            screen = Screen.GAME
            autosave()
            if (!introPending) pump()
        }
    }

    /** Main lagi dengan pengaturan yang sama dan seed baru. */
    fun playAgain() {
        val c = lastConfig ?: return go(Screen.SETUP)
        newGame(c, kotlin.random.Random.nextLong(1, 1_000_000_000))
    }

    fun startTutorial() {
        val c = content ?: return
        val config = GameConfig(
            players = listOf(
                PlayerSetup(if (lang == Lang.EN) "You" else "Kamu", Faction.MI6),
                PlayerSetup("Bot Easy", Faction.OSS, isBot = true, botLevel = BotLevel.EASY),
                PlayerSetup("Bot Easy 2", Faction.RESISTANCE, isBot = true, botLevel = BotLevel.EASY),
            ),
            balance = c.balance,
            mode = GameMode.STANDARD,
        )
        newGame(config, seed = 1944, intro = true, asTutorial = true)
    }

    fun tutorialSeen(kind: String) {
        val t = tutorial ?: return
        if (kind !in t.seen) tutorial = t.copy(seen = t.seen + kind)
    }

    fun dismissTutorial() {
        tutorial = tutorial?.copy(dismissed = true)
        updateSettings(settings.copy(tutorialSeen = true))
    }

    fun introDone() {
        if (!introPending) return
        introPending = false
        pump()
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
                botJob?.cancel()
                session = it
                lastConfig = it.state.config
                endSeen = it.isOver
                tutorial = null
                introPending = false
                cues.clear()
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
                    if (action is Action.EndTurn) tutorial?.let { if (!it.finished) tutorial = it.copy(finished = true) }
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
        cues.clear()
    }

    fun consumeCue() {
        if (cues.isNotEmpty()) cues.removeAt(0)
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
            tutorial = null
            cues.clear()
            go(Screen.MENU)
        }
    }

    fun showEnd() {
        endSeen = true
        screen = Screen.END
    }

    fun uiSound(s: Sfx) = sound.play(s, 0.6f)

    // --- Catatan detektif manual ------------------------------------------------------------

    fun marks(): Map<String, String> {
        val u = ui ?: return emptyMap()
        return notesStore.marks(u.gameId, u.view.viewerId.value)
    }

    fun setMark(card: CardId, column: String, mark: String?) {
        val u = ui ?: return
        notesStore.setMark(u.gameId, u.view.viewerId.value, "${card.value}|$column", mark)
        notesVersion++
    }

    fun note(card: CardId): String {
        val u = ui ?: return ""
        return notesStore.marks(u.gameId, u.view.viewerId.value)["${card.value}|note"].orEmpty()
    }

    fun setNote(card: CardId, text: String) {
        val u = ui ?: return
        notesStore.setMark(u.gameId, u.view.viewerId.value, "${card.value}|note", text.ifBlank { null })
        notesVersion++
    }

    var notesVersion by mutableStateOf(0)
        private set

    // --- Loop bot ---------------------------------------------------------------------------

    private fun pump() {
        if (botJob?.isActive == true) return
        botJob = viewModelScope.launch {
            try {
                var steps = 0
                while (true) {
                    while (true) {
                        val s = session ?: break
                        if (introPending) break
                        val botTurn = mutex.withLock { s.isBotTurn }
                        if (!botTurn) break
                        val d = if (fastForward) 0L else settings.speed.delayMs
                        if (d > 0) delay(d)
                        val r = withContext(Dispatchers.Default) { mutex.withLock { s.stepBot() } }
                        steps++
                        val events = (r as? StepResult.Ok)?.events.orEmpty()
                        if (d > 0 || steps % 25 == 0) publish(events)
                        if (steps % 40 == 0) autosave()
                        // Beri waktu isyarat penting (Front, cap) untuk tampil sebelum bot melanjutkan.
                        if (d > 0 && cues.any { it is Cue.Front || it is Cue.Stamp }) delay(1600)
                    }
                    fastForward = false
                    publish(emptyList())
                    autosave()
                    // Manusia bisa bertindak saat publish/autosave di atas masih berjalan (pump() lain lalu
                    // diabaikan karena job ini masih aktif) — periksa lagi sebelum berhenti.
                    val s = session ?: break
                    if (introPending || !mutex.withLock { s.isBotTurn }) break
                }
                if (session?.isOver == true && !endSeen) {
                    delay(if (settings.speed == Speed.INSTANT) 0 else 1800)
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

    private fun gameId(s: GameSession) = "${s.state.seed}-" + s.state.players.joinToString("|") { "${it.name}:${it.faction}" }.hashCode()

    private suspend fun publish(events: List<GameEvent>, message: String? = null) {
        val s = session ?: return
        val c = content ?: return
        val e = engine ?: return
        val wantPossibilities = settings.showPossibilities
        val (seq, snap) = withContext(Dispatchers.Default) {
            mutex.withLock {
                val seq = ++snapshotSeq
                val view = s.view()
                val logText = LogText(Names(c, lang))
                val names = logText.playerNames(view)
                val log = logText.lines(view, limit = 200).filter { settings.showSecretsInLog || it.kind != LogKind.SECRET }
                val poss = if (wantPossibilities && !s.isOver) {
                    runCatching {
                        val r = tracker!!.solve(DeductionInput.fromView(view, c))
                        Possibilities(
                            combos = r.comboProb.count { it.value > 1e-9 },
                            envelopeProb = r.envelopeProb,
                            best = r.best?.first?.cards,
                            bestProb = r.bestProbability,
                        )
                    }.getOrNull()
                } else {
                    null
                }
                seq to GameUi(
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
                    possibilities = poss,
                    gameId = gameId(s),
                    seed = s.state.seed,
                )
            }
        }
        // Snapshot diambil di dalam mutex; yang lebih lama tidak boleh menimpa yang lebih baru.
        if (seq > shownSeq) {
            shownSeq = seq
            ui = snap
            react(snap, events)
            if (snap.isOver) recordArchive(snap)
        }
    }

    /** Suara, getaran, dan antrean animasi dari event terbaru. */
    private fun react(snap: GameUi, events: List<GameEvent>) {
        if (events.isEmpty()) return
        val list = Cue.from(events, snap.view.viewerId)
        var sounds = 0
        for (c in list) {
            val sfx = c.sfx
            if (sfx != null && sounds < 3 && !(c is Cue.Money && c.amount == 0)) {
                sound.play(sfx)
                sounds++
            }
            c.haptic?.let(haptics::buzz)
        }
        val animate = !settings.reduceMotion && settings.speed != Speed.INSTANT && !fastForward
        if (!animate) return
        for (c in list) {
            val important = c is Cue.Front || c is Cue.Stamp
            val visual = important || c is Cue.Dice || c is Cue.CardDrawn || c is Cue.Jackpot || (c is Cue.Money && c.amount > 0) || c is Cue.Tactic
            if (!visual) continue
            if (cues.size >= 6 && !important) continue
            cues.add(c)
        }
    }

    private fun recordArchive(snap: GameUi) {
        val s = session ?: return
        val win = snap.view.winner ?: return
        val scores = snap.scores.orEmpty().associateBy { it.player.value }
        val entry = ArchiveEntry(
            id = snap.gameId,
            endedAt = System.currentTimeMillis(),
            seed = s.state.seed,
            mode = s.state.config.mode.name,
            winType = win.type.name,
            rounds = snap.view.roundNumber,
            warStep = snap.view.warStep,
            players = s.state.players.map { p ->
                ArchivePlayer(
                    name = p.name,
                    faction = p.faction.name,
                    bot = p.isBot,
                    level = p.botLevel?.name,
                    score = scores[p.id.value]?.total ?: 0,
                    winner = p.id in win.winners,
                )
            },
        )
        viewModelScope.launch(Dispatchers.IO) {
            archiveStore.add(entry)
            val all = archiveStore.all()
            withContext(Dispatchers.Main) { archive = all }
        }
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
