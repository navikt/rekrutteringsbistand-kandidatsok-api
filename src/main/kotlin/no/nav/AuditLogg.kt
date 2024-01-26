package no.nav

import no.nav.common.audit_log.cef.AuthorizationDecision
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl
import org.slf4j.LoggerFactory


object AuditLogg {

    private val secureLog = LoggerFactory.getLogger("secureLog")!!
    private val auditLogger: AuditLogger = AuditLoggerImpl()

    private fun log(cefMessage: CefMessage) {
        auditLogger.log(cefMessage)
        secureLog.info("auditlogger: {}", cefMessage)
    }

    fun loggOppslagCv(aktørId: String, navIdent: String) {
        lagCefMessage(navIdent = navIdent, aktørId = aktørId, msg = "NAV-ansatt har åpnet CV'en til bruker")
            .apply(::log)
    }

    fun loggOppslagKandidatsammendrag(aktørId: String, navIdent: String) {
        lagCefMessage(navIdent = navIdent, aktørId = aktørId, msg = "NAV-ansatt har åpnet en stilling i kontekst av kandidat med kandidatsammendragsinformasjon")
            .apply(::log)
    }

    private fun lagCefMessage(navIdent: String, aktørId: String, msg: String): CefMessage =
        CefMessage.builder()
            .applicationName("Rekrutteringsbistand")
            .loggerName("rekrutteringsbistand-kandidatsok-api")
            .event(CefMessageEvent.ACCESS)
            .name("Sporingslogg")
            .authorizationDecision(AuthorizationDecision.PERMIT)
            .sourceUserId(navIdent)
            .destinationUserId(aktørId)
            .timeEnded(System.currentTimeMillis())
            .extension("msg", msg)
            .build()
}
