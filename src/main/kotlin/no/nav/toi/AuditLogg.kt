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

    private fun log(cefMessage: CefMessage) {
        auditLogger.log(cefMessage)
        secureLog.info("auditlogger: {}", cefMessage)
    }

    fun loggOppslagCv(userid: String, navIdent: String) {
        lagCefMessage(navIdent = navIdent, userid = userid, msg = "NAV-ansatt har åpnet CV'en til bruker")
            .apply(AuditLogg::log)
    }

    fun loggOppslagKandidatsammendrag(userid: String, navIdent: String) {
        lagCefMessage(navIdent = navIdent, userid = userid, msg = "NAV-ansatt har åpnet en stilling i kontekst av kandidat med kandidat sammendragsinformasjon")
            .apply(AuditLogg::log)
    }

    fun loggOppslagKandidatStillingssøk(userid: String, navIdent: String) {
        lagCefMessage(navIdent = navIdent, userid = userid, msg = "NAV-ansatt har åpnet en stilling i kontekst av kandidat med kandidat stillingssøksinformasjon")
            .apply(AuditLogg::log)
    }

    private fun lagCefMessage(navIdent: String, userid: String, msg: String): CefMessage =
        CefMessage.builder()
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
}
