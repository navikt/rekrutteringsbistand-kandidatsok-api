package no.nav.toi

import no.nav.common.audit_log.cef.AuthorizationDecision
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl
import org.slf4j.LoggerFactory


object AuditLogg {
    private val secureLog = LoggerFactory.getLogger("secureLog")!!
    private val auditLogger: AuditLogger = AuditLoggerImpl()

    fun loggOppslagCv(userid: String, navIdent: String, permit: Boolean) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har åpnet CV'en til bruker",
            authorizationDecision = if (permit) AuthorizationDecision.PERMIT else AuthorizationDecision.DENY
        )
    }

    fun loggOppslagKandidatsammendrag(userid: String, navIdent: String, permit: Boolean) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har sett kontaktinformasjon og informasjon om veileder fra brukers CV",
            authorizationDecision = if (permit) AuthorizationDecision.PERMIT else AuthorizationDecision.DENY
        )
    }

    fun loggOppslagKandidatStillingssøk(userid: String, navIdent: String) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har sett etter stilling for kandidat, med jobbprofil fra brukers CV"
        )
    }

    fun loggOppslagNavn(userid: String, navIdent: String) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har hentet navn til bruker basert på fødselsnummer"
        )
    }

    private fun logCefMessage(
        navIdent: String,
        userid: String,
        msg: String,
        authorizationDecision: AuthorizationDecision = AuthorizationDecision.PERMIT
    ) {
        val message = CefMessage.builder()
            .applicationName("Rekrutteringsbistand")
            .loggerName("rekrutteringsbistand-kandidatsok-api")
            .event(CefMessageEvent.ACCESS)
            .name("Sporingslogg")
            .authorizationDecision(authorizationDecision)
            .sourceUserId(navIdent)
            .destinationUserId(userid)
            .timeEnded(System.currentTimeMillis())
            .extension("msg", msg)
            .build()
        auditLogger.log(message)
        secureLog.info("auditlogger: {}", message)
    }

    fun loggSpesifiktKandidatsøk(userid: String, navIdent: String, fikkTreff: Boolean) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har gjort spesifikt kandidatsøk på brukeren",
            authorizationDecision = if (fikkTreff) AuthorizationDecision.PERMIT else AuthorizationDecision.DENY
        )
    }

    fun loggGenereltKandidatsøk(fritekst: String?, navIdent: String) {
        logCefMessage(
            navIdent = navIdent,
            userid = "",
            msg = "NAV-ansatt har gjort generelt kandidatsøk" +
                    (fritekst?.let { " med fritekst $it" } ?: ""),
            authorizationDecision = AuthorizationDecision.PERMIT
        )
    }

}
