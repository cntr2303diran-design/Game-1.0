package id.diran.lisbon1944.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimArgsTest {

    @Test
    fun `validate dan balance terbaca`() {
        assertEquals(SimArgs(validateOnly = true, balancePath = "b.json"), SimArgs.parse(arrayOf("--validate", "--balance", "b.json")))
    }

    @Test
    fun `argumen tidak dikenal ditolak`() {
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(arrayOf("--terbang")) }
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(arrayOf("--validate", "--balance")) }
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(emptyArray()) }
    }

    @Test
    fun `validasi konten bawaan sukses`() {
        val out = StringBuilder()
        assertEquals(0, runValidate(SimArgs(validateOnly = true), out))
        assertTrue(out.contains("konten valid"))
        assertTrue(out.contains("216 jawaban amplop"))
    }

    @Test
    fun `balance alternatif yang rusak ditolak`() {
        val f = File.createTempFile("balance", ".json")
        try {
            val text = javaClass.getResource("/content/balance.json")!!.readText().replace("\"hqStock\": 12", "\"hqStock\": 0")
            f.writeText(text)
            val out = StringBuilder()
            assertEquals(1, runValidate(SimArgs(validateOnly = true, balancePath = f.path), out))
            assertTrue(out.contains("hqStock harus positif"))
        } finally {
            f.delete()
        }
    }
}
