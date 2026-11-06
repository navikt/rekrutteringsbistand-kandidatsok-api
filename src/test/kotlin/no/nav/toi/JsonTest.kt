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
        assertThat(javalinJsonMapper().fromJsonString<MedNorskeTegn>(json, MedNorskeTegn::class.java))
            .isEqualTo(MedNorskeTegn("Oslo", true, "ja"))
    }

    @Test
    fun `http-klientens mapper serialiserer felt som starter med æ, ø og å`() {
        val json = httpKlientMapper.writeValueAsString(MedNorskeTegn("Oslo", true, "ja"))

        assertThat(json).contains("\"ønsketSted\":\"Oslo\"", "\"åpen\":true", "\"ærlig\":\"ja\"")
        assertThat(httpKlientMapper.readValue<MedNorskeTegn>(json))
            .isEqualTo(MedNorskeTegn("Oslo", true, "ja"))
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
        assertThat(httpKlientMapper.readValue<MedPrimitiver>("""{"tall":1}"""))
            .isEqualTo(MedPrimitiver(1, false))
        assertThat(httpKlientMapper.readValue<MedPrimitiver>("""{"tall":1,"flagg":true} x"""))
            .isEqualTo(MedPrimitiver(1, true))
    }

    @Test
    fun `begge mapperne avviser tom og ugyldig JSON`() {
        listOf("", "not-json", """{"tall":""").forEach { json ->
            assertThatThrownBy {
                javalinJsonMapper().fromJsonString<MedPrimitiver>(json, MedPrimitiver::class.java)
            }.isInstanceOf(tools.jackson.core.JacksonException::class.java)
            assertThatThrownBy {
                httpKlientMapper.readValue<MedPrimitiver>(json)
            }.isInstanceOf(tools.jackson.core.JacksonException::class.java)
        }
    }
}
