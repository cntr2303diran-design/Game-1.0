package id.diran.lisbon1944.sim

import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.GameMode

/** Argumen CLI simulasi (BAGIAN 11). */
data class SimArgs(
    val validateOnly: Boolean = false,
    val games: Int = 1000,
    val players: Int = 4,
    val bots: List<BotLevel> = emptyList(),
    val seed: Long = 42,
    val balancePath: String? = null,
    val mode: GameMode = GameMode.STANDARD,
    val out: String? = null,
    val maxActions: Int = DEFAULT_MAX_ACTIONS,
    val abTest: Boolean = true,
    val threads: Int = Runtime.getRuntime().availableProcessors(),
    val secondsPerTurn: Int = 45,
) {
    /** Tingkat bot per kursi (diulang/dipotong agar sesuai jumlah pemain; default Normal). */
    val seatLevels: List<BotLevel>
        get() = if (bots.isEmpty()) List(players) { BotLevel.NORMAL } else List(players) { bots[it % bots.size] }

    companion object {
        /** Permainan 4 jam ≈ 1.100–1.500 aksi; di atas batas ini dihentikan dan dicatat sebagai anomali. */
        const val DEFAULT_MAX_ACTIONS = 4_000

        const val USAGE = """Pemakaian:
  ./gradlew :sim:run --args="--validate [--balance path/balance.json]"
  ./gradlew :sim:run --args="--games 1000 --players 4 --bots hard,hard,normal,normal
      --seed 42 --balance path/balance.json --mode standard --out report.csv"

  --validate            Muat & validasi seluruh konten, cetak ringkasan
  --games N             Jumlah permainan (default 1000)
  --players N           3–6 pemain (default 4)
  --bots a,b,...        easy|normal|hard per kursi (default normal semua)
  --seed N              Seed pertama; permainan ke-i memakai seed+i (default 42)
  --balance PATH        balance.json alternatif
  --mode M              standard|quick|family|campaign (default standard)
  --out PATH            Tulis CSV per permainan (+ PATH-summary.csv berisi metrik & A/B)
  --max-actions N       Batas aksi per permainan sebelum dianggap anomali (default 4000)
  --no-ab               Lewati perbandingan A/B infoPaymentQuota = 1
  --threads N           Jumlah thread (default jumlah CPU)
  --seconds-per-turn N  Perkiraan detik per giliran untuk durasi (default 45)"""

        fun parse(args: Array<String>): SimArgs {
            var r = SimArgs()
            var simulate = false
            var i = 0
            fun value(name: String): String = args.getOrNull(++i) ?: throw IllegalArgumentException("$name membutuhkan nilai\n$USAGE")
            fun int(name: String): Int = value(name).toIntOrNull() ?: throw IllegalArgumentException("$name harus bilangan bulat")
            while (i < args.size) {
                when (val a = args[i]) {
                    "--validate" -> r = r.copy(validateOnly = true)
                    "--games" -> { r = r.copy(games = int(a)); simulate = true }
                    "--players" -> { r = r.copy(players = int(a)); simulate = true }
                    "--bots" -> {
                        r = r.copy(bots = value(a).split(",").map { name ->
                            BotLevel.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                                ?: throw IllegalArgumentException("Tingkat bot tidak dikenal: $name (easy|normal|hard)")
                        })
                        simulate = true
                    }
                    "--seed" -> { r = r.copy(seed = value(a).toLongOrNull() ?: throw IllegalArgumentException("--seed harus angka")); simulate = true }
                    "--balance" -> r = r.copy(balancePath = value(a))
                    "--mode" -> {
                        val m = value(a)
                        r = r.copy(mode = GameMode.entries.firstOrNull { it.name.equals(m, ignoreCase = true) }
                            ?: throw IllegalArgumentException("Mode tidak dikenal: $m"))
                        simulate = true
                    }
                    "--out" -> { r = r.copy(out = value(a)); simulate = true }
                    "--max-actions" -> r = r.copy(maxActions = int(a))
                    "--no-ab" -> r = r.copy(abTest = false)
                    "--threads" -> r = r.copy(threads = int(a).coerceAtLeast(1))
                    "--seconds-per-turn" -> r = r.copy(secondsPerTurn = int(a))
                    "--help", "-h" -> throw IllegalArgumentException(USAGE)
                    else -> throw IllegalArgumentException("Argumen tidak dikenal: $a\n$USAGE")
                }
                i++
            }
            if (!r.validateOnly && !simulate) throw IllegalArgumentException("Tidak ada perintah.\n$USAGE")
            if (r.players !in 3..6) throw IllegalArgumentException("--players harus 3–6")
            if (r.games < 1) throw IllegalArgumentException("--games minimal 1")
            if (r.bots.isNotEmpty() && r.bots.size != r.players) {
                throw IllegalArgumentException("--bots harus berisi ${r.players} tingkat (satu per kursi), ditemukan ${r.bots.size}")
            }
            return r
        }
    }
}
