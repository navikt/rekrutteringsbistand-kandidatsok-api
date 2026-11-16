package no.nav.toi;

import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;

/**
 * @deprecated Hotfix for å fikse issue i opensearch versjon 3.11.0 <a href="https://github.com/opensearch-project/opensearch-java/issues/2293">#2293</a>
 */
@Deprecated()
public final class OpenSearchJsonMapper {
    private OpenSearchJsonMapper() {}

    /**
     * @deprecated Hotfix for å fikse issue i opensearch versjon 3.11.0 <a href="https://github.com/opensearch-project/opensearch-java/issues/2293">#2293</a>
     */
    @Deprecated()
    public static JsonpMapper create() {
        return new JacksonJsonpMapper();
    }
}
