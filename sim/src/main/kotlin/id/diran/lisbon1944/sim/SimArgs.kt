package id.diran.lisbon1944.sim

/** Argumen CLI simulasi. Tahap 1 hanya memakai --validate dan --balance. */
data class SimArgs(
    val validateOnly: Boolean = false,
    val balancePath: String? = null,
) {
    companion object {
        const val USAGE = """Pemakaian:
  ./gradlew :sim:run --args="--validate [--balance path/balance.json]"

  --validate          Muat & validasi seluruh konten, cetak ringkasan
  --balance <path>    Validasi balance.json alternatif dari disk"""

        fun parse(args: Array<String>): SimArgs {
            var result = SimArgs()
            var i = 0
            while (i < args.size) {
                when (val a = args[i]) {
                    "--validate" -> result = result.copy(validateOnly = true)
                    "--balance" -> {
                        val v = args.getOrNull(i + 1) ?: throw IllegalArgumentException("--balance membutuhkan path")
                        result = result.copy(balancePath = v)
                        i++
                    }
                    "--help", "-h" -> throw IllegalArgumentException(USAGE)
                    else -> throw IllegalArgumentException("Argumen tidak dikenal: $a\n$USAGE")
                }
                i++
            }
            if (!result.validateOnly) throw IllegalArgumentException("Tidak ada perintah.\n$USAGE")
            return result
        }
    }
}
