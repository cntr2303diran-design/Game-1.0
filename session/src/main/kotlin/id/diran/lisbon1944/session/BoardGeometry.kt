package id.diran.lisbon1944.session

/** Sisi papan tempat sebuah petak berada (untuk orientasi strip warna distrik). */
enum class Side { BOTTOM, LEFT, TOP, RIGHT }

data class Cell(val row: Int, val col: Int, val side: Side, val corner: Boolean)

/**
 * Tata letak papan persegi 11×11: petak 0 (START) di pojok kanan bawah, berjalan searah jarum jam
 * ke kiri sepanjang sisi bawah (seperti papan fisik).
 */
object BoardGeometry {
    const val GRID = 11
    const val TILES = 40

    fun cell(index: Int): Cell {
        require(index in 0 until TILES) { "indeks petak $index" }
        val corner = index % 10 == 0
        return when {
            index <= 10 -> Cell(10, 10 - index, Side.BOTTOM, corner)
            index < 20 -> Cell(10 - (index - 10), 0, Side.LEFT, corner)
            index <= 30 -> Cell(0, index - 20, Side.TOP, corner)
            else -> Cell(index - 30, 10, Side.RIGHT, corner)
        }
    }

    /** Petak-petak yang dilewati dari [from] sejauh [steps] (untuk animasi gerak), tidak termasuk [from]. */
    fun path(from: Int, steps: Int): List<Int> =
        if (steps >= 0) (1..steps).map { (from + it) % TILES }
        else (1..-steps).map { ((from - it) % TILES + TILES) % TILES }
}
