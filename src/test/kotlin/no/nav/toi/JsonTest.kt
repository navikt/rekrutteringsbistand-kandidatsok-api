package no.nav.toi

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class JsonTest {
    private data class MedNorskeTegn(val ønsketSted: String, val åpen: Boolean, val ærlig: String)

    @Test
    fun `javalin-mapperen serialiserer felt som starter med æ, ø og å`() {
        val json = javalinJsonMapper().toJsonString(MedNorskeTegn("Oslo", true, "ja"), MedNorskeTegn::class.java)

        assertThat(json).contains("\"ønsketSted\":\"Oslo\"", "\"åpen\":true", "\"ærlig\":\"ja\"")
    }

    @Test
    fun `http-klientens mapper serialiserer felt som starter med æ, ø og å`() {
        val json = httpKlientMapper.writeValueAsString(MedNorskeTegn("Oslo", true, "ja"))

        assertThat(json).contains("\"ønsketSted\":\"Oslo\"", "\"åpen\":true", "\"ærlig\":\"ja\"")
    }

    private data class MedPrimitiver(val tall: Int, val flagg: Boolean)

    @Test
    fun `javalin-mapperen godtar null i primitive felt og avviser ukjente felt`() {
        val mapper = javalinJsonMapper()

        assertThat(mapper.fromJsonString<MedPrimitiver>("""{"tall":null,"flagg":null}""", MedPrimitiver::class.java))
            .isEqualTo(MedPrimitiver(0, false))
        assertThat(mapper.fromJsonString<MedPrimitiver>("""{"tall":1}""", MedPrimitiver::class.java))
            .isEqualTo(MedPrimitiver(1, false))
        assertThat(mapper.fromJsonString<MedPrimitiver>("""{"tall":1,"flagg":true} x""", MedPrimitiver::class.java))
            .isEqualTo(MedPrimitiver(1, true))
        assertThatThrownBy { mapper.fromJsonString<MedPrimitiver>("""{"tall":1,"flagg":true,"ukjent":1}""", MedPrimitiver::class.java) }
    }

    @Test
    fun `http-klientens mapper godtar null i primitive felt og ignorerer ukjente felt`() {
        assertThat(httpKlientMapper.readValue<MedPrimitiver>("""{"tall":null,"flagg":null,"ukjent":1}"""))
            .isEqualTo(MedPrimitiver(0, false))
    }
}
