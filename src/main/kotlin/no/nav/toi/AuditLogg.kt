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

    fun loggOppslagCv(userid: String, navIdent: String) {
        logCefMessage(navIdent = navIdent, userid = userid, msg = "NAV-ansatt har åpnet CV'en til bruker")
    }

    fun loggOppslagKandidatsammendrag(userid: String, navIdent: String) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har åpnet en stilling i kontekst av kandidat med kandidat sammendragsinformasjon"
        )
    }

    fun loggOppslagKandidatStillingssøk(userid: String, navIdent: String) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har åpnet en stilling i kontekst av kandidat med kandidat stillingssøksinformasjon"
        )
    }

    private fun logCefMessage(navIdent: String, userid: String, msg: String) {
        val message = CefMessage.builder()
            .applicationName("Rekrutteringsbistand")
            .loggerName("rekrutteringsbistand-kandidatsok-api")
            .event(CefMessageEvent.ACCESS)
            .name("Sporingslogg")
            .authorizationDecision(AuthorizationDecision.PERMIT)
            .sourceUserId(navIdent)
            .destinationUserId(userid)
            .timeEnded(System.currentTimeMillis())
            .extension("msg", msg)
            .build()
        auditLogger.log(message)
        secureLog.info("auditlogger: {}", message)
    }

    fun loggOppslagKAndidatsøk(userid: String, navIdent: String) {
        logCefMessage(
            navIdent = navIdent,
            userid = userid,
            msg = "NAV-ansatt har gjort spesifikt kandidatsøk på brukeren."
        )
    }
}
