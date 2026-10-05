package no.nav.toi

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.status.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MarkerFactory
import java.io.File

/**
 * Verifiserer at logback.xml i rotkatalogen (den som brukes i Docker-imaget) ruter audit- og team-logs-meldinger,
 * som kan inneholde fødselsnummer, til riktig sted og aldri til den vanlige applikasjonsloggen.
 */
class LogbackKonfigurasjonTest {
    private val konsoll = "loggIJsonFormatTilKibana"
    private val teamLogs = "team-logs"
    private val audit = "AuditLogger"

    private val kontekster = mutableListOf<LoggerContext>()

    @AfterEach
    fun stoppKontekster() = kontekster.forEach(LoggerContext::stop)

    @Test
    fun `audit-meldinger går bare til audit-appenderen`() {
        val ctx = lastProduksjonskonfigurasjon()

        assertThat(ctx.getLogger(audit).isAdditive).isFalse()
        assertThat(appendereSomMottar(ctx, audit, event(ctx, audit, medTeamLogsMarker = false)))
            .containsExactly(audit)
    }

    @Test
    fun `team-logs-meldinger går bare til team-logs`() {
        val ctx = lastProduksjonskonfigurasjon()

        assertThat(appendereSomMottar(ctx, "no.nav.toi.Noe", event(ctx, "no.nav.toi.Noe", medTeamLogsMarker = true)))
            .containsExactly(teamLogs)
    }

    @Test
    fun `vanlige meldinger går bare til konsoll`() {
        val ctx = lastProduksjonskonfigurasjon()

        assertThat(appendereSomMottar(ctx, "no.nav.toi.Noe", event(ctx, "no.nav.toi.Noe", medTeamLogsMarker = false)))
            .containsExactly(konsoll)
    }

    @Test
    fun `root har bare konsoll og team-logs`() {
        val ctx = lastProduksjonskonfigurasjon()

        assertThat(appenderNavn(ctx.getLogger(Logger.ROOT_LOGGER_NAME)))
            .containsExactlyInAnyOrder(konsoll, teamLogs)
    }

    @Test
    fun `konfigurasjonen gir ingen feil`() {
        val ctx = lastProduksjonskonfigurasjon()

        val feil = ctx.statusManager.copyOfStatusList.filter { it.effectiveLevel >= Status.ERROR }
        assertThat(feil).isEmpty()
    }

    @Test
    fun `rutingen er lik uten NAIS_CLUSTER_NAME`() {
        val ctx = lastProduksjonskonfigurasjon(naisClusterName = null)

        assertThat(appendereSomMottar(ctx, audit, event(ctx, audit, medTeamLogsMarker = false)))
            .containsExactly(audit)
        assertThat(appendereSomMottar(ctx, "no.nav.toi.Noe", event(ctx, "no.nav.toi.Noe", medTeamLogsMarker = true)))
            .containsExactly(teamLogs)
    }

    private fun lastProduksjonskonfigurasjon(naisClusterName: String? = "test-gcp"): LoggerContext {
        val ctx = LoggerContext().also(kontekster::add)
        naisClusterName?.let { ctx.putProperty("NAIS_CLUSTER_NAME", it) }
        JoranConfigurator().apply { context = ctx }.doConfigure(File("logback.xml"))
        return ctx
    }

    private fun event(ctx: LoggerContext, loggerName: String, medTeamLogsMarker: Boolean): ILoggingEvent =
        LoggingEvent(Logger::class.java.name, ctx.getLogger(loggerName), Level.INFO, "melding", null, null).apply {
            if (medTeamLogsMarker) addMarker(MarkerFactory.getMarker("TEAM_LOGS"))
        }

    /**
     * Følger logger-hierarkiet slik logback gjør (stopper ved additivity=false) og returnerer navnet på
     * appenderne som ville skrevet eventet, etter at appenderens filtre er vurdert.
     */
    private fun appendereSomMottar(ctx: LoggerContext, loggerName: String, event: ILoggingEvent): List<String> =
        loggerHierarki(ctx, loggerName)
            .flatMap { it.iteratorForAppenders().asSequence().toList() }
            .filter { it.slipperGjennom(event) }
            .map { it.name }

    private fun loggerHierarki(ctx: LoggerContext, loggerName: String): List<Logger> {
        val navn = loggerName.split(".").let { deler -> deler.indices.reversed().map { deler.take(it + 1).joinToString(".") } }
        val kjede = navn.mapNotNull(ctx::exists) + ctx.getLogger(Logger.ROOT_LOGGER_NAME)
        val ikkeAdditiv = kjede.indexOfFirst { !it.isAdditive }
        return if (ikkeAdditiv == -1) kjede else kjede.take(ikkeAdditiv + 1)
    }

    private fun Appender<ILoggingEvent>.slipperGjennom(event: ILoggingEvent): Boolean {
        for (filter in copyOfAttachedFiltersList) {
            when (filter.decide(event)) {
                FilterReply.DENY -> return false
                FilterReply.ACCEPT -> return true
                FilterReply.NEUTRAL -> continue
            }
        }
        return true
    }

    private fun appenderNavn(logger: Logger) = logger.iteratorForAppenders().asSequence().map { it.name }.toList()
}
