package com.agentsystem.alert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PythFeedResolverTest {

    PythFeedResolver resolver;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        resolver = new PythFeedResolver(mapper);
    }

    // Real response captured from GET https://hermes.pyth.network/v2/price_feeds?query=QQQ&asset_type=equity —
    // 7 candidates for a single symbol: a leveraged pair (TQQQ/SQQQ), an alternate ETF (QQQM), and
    // deprecated pre/post/overnight-market variants of QQQ itself alongside the plain regular-hours feed.
    private static final String QQQ_FIXTURE = """
            [
              {
                "id": "433b196b3b026f46f76b5e901c84c575a7280dcba0f4272edefe0529b599ad64",
                "attributes": {"asset_type":"Equity","base":"QQQM","country":"US","description":"INVESCO NASDAQ 100 ETF / US DOLLAR","display_symbol":"QQQM/USD","quote_currency":"USD","symbol":"Equity.US.QQQM/USD"}
              },
              {
                "id": "0eda5e8f3e5881e7e64971b02359250f9d70977e63940c4c9c0d77f54195f13e",
                "attributes": {"asset_type":"Equity","base":"QQQ","country":"US","description":"DEPRECATED FEED - INVESCO QQQ TRUST SERIES 1 / US DOLLAR - OVERNIGHT HOURS","display_symbol":"QQQ/USD OVERNIGHT","quote_currency":"USD","symbol":"Equity.US.QQQ/USD.ON"}
              },
              {
                "id": "f207c5d325e44579b12965394d9a4dd988567de635a494694bfb0b46c20a06ec",
                "attributes": {"asset_type":"Equity","base":"SQQQ","country":"US","description":"PROSHARES ULTRAPRO SHORT QQQ / US DOLLAR","display_symbol":"SQQQ/USD","quote_currency":"USD","symbol":"Equity.US.SQQQ/USD"}
              },
              {
                "id": "5aa9f82dc2e0f5f8271fd163e980010101517da59f4b72b71c7056a5950b2f9d",
                "attributes": {"asset_type":"Equity","base":"TQQQ","country":"US","description":"PROSHARES ULTRAPRO QQQ / US DOLLAR","display_symbol":"TQQQ/USD","quote_currency":"USD","symbol":"Equity.US.TQQQ/USD"}
              },
              {
                "id": "9695e2b96ea7b3859da9ed25b7a46a920a776e2fdae19a7bcfdf2b219230452d",
                "attributes": {"asset_type":"Equity","base":"QQQ","country":"US","description":"INVESCO QQQ TRUST SERIES 1 / US DOLLAR","display_symbol":"QQQ/USD","quote_currency":"USD","symbol":"Equity.US.QQQ/USD"}
              },
              {
                "id": "e0746896538f836f754adae0aff16859b33344736cbd85f2e36fb8ca057b9d26",
                "attributes": {"asset_type":"Equity","base":"QQQ","country":"US","description":"DEPRECATED FEED - INVESCO QQQ TRUST SERIES 1 / US DOLLAR - POST MARKET HOURS","display_symbol":"QQQ/USD POST MARKET","quote_currency":"USD","symbol":"Equity.US.QQQ/USD.POST"}
              },
              {
                "id": "fbbbc98c9d0591ad0ca0b0e53ff2efb955fef8958ffa6890f5a3599e91ec1d49",
                "attributes": {"asset_type":"Equity","base":"QQQ","country":"US","description":"DEPRECATED FEED - INVESCO QQQ TRUST SERIES 1 / US DOLLAR - PRE MARKET HOURS","display_symbol":"QQQ/USD PRE MARKET","quote_currency":"USD","symbol":"Equity.US.QQQ/USD.PRE"}
              }
            ]
            """;

    private static final String EXPECTED_QQQ_ID = "9695e2b96ea7b3859da9ed25b7a46a920a776e2fdae19a7bcfdf2b219230452d";

    @Test
    void pickBestMatch_picksPlainRegularHoursFeed_overDeprecatedAndLeveragedVariants() throws Exception {
        JsonNode results = mapper.readTree(QQQ_FIXTURE);

        Optional<String> id = callPickBestMatch(results, "QQQ");

        assertThat(id).contains(EXPECTED_QQQ_ID);
    }

    @Test
    void pickBestMatch_caseInsensitiveBaseMatch() throws Exception {
        JsonNode results = mapper.readTree(QQQ_FIXTURE);

        Optional<String> id = callPickBestMatch(results, "qqq");

        assertThat(id).contains(EXPECTED_QQQ_ID);
    }

    @Test
    void pickBestMatch_noMatchingBase_returnsEmpty() throws Exception {
        JsonNode results = mapper.readTree(QQQ_FIXTURE);

        Optional<String> id = callPickBestMatch(results, "AAPL");

        assertThat(id).isEmpty();
    }

    @Test
    void pickBestMatch_emptyResults_returnsEmpty() throws Exception {
        JsonNode results = mapper.readTree("[]");

        Optional<String> id = callPickBestMatch(results, "QQQ");

        assertThat(id).isEmpty();
    }

    @Test
    void pickBestMatch_onlyDeprecatedCandidates_returnsEmpty() throws Exception {
        String fixture = """
                [
                  {"id":"dep-1","attributes":{"base":"XYZ","quote_currency":"USD","description":"DEPRECATED FEED - XYZ / US DOLLAR","display_symbol":"XYZ/USD"}}
                ]
                """;
        JsonNode results = mapper.readTree(fixture);

        Optional<String> id = callPickBestMatch(results, "XYZ");

        assertThat(id).isEmpty();
    }

    @Test
    void pickBestMatch_nonUsdQuote_isExcluded() throws Exception {
        String fixture = """
                [
                  {"id":"eur-1","attributes":{"base":"BTC","quote_currency":"EUR","description":"BITCOIN / EURO","display_symbol":"BTC/EUR"}},
                  {"id":"usd-1","attributes":{"base":"BTC","quote_currency":"USD","description":"BITCOIN / US DOLLAR","display_symbol":"BTC/USD"}}
                ]
                """;
        JsonNode results = mapper.readTree(fixture);

        Optional<String> id = callPickBestMatch(results, "BTC");

        assertThat(id).contains("usd-1");
    }

    @Test
    void pickBestMatch_singleNonPlainCandidate_stillReturnedAsFallback() throws Exception {
        // Only an overnight-hours variant exists (no plain "SYMBOL/USD" feed) — should still
        // fall back to it rather than returning empty.
        String fixture = """
                [
                  {"id":"on-1","attributes":{"base":"XYZ","quote_currency":"USD","description":"XYZ / US DOLLAR - OVERNIGHT","display_symbol":"XYZ/USD OVERNIGHT"}}
                ]
                """;
        JsonNode results = mapper.readTree(fixture);

        Optional<String> id = callPickBestMatch(results, "XYZ");

        assertThat(id).contains("on-1");
    }

    @SuppressWarnings("unchecked")
    private Optional<String> callPickBestMatch(JsonNode results, String base) throws Exception {
        Method m = PythFeedResolver.class.getDeclaredMethod("pickBestMatch", JsonNode.class, String.class);
        m.setAccessible(true);
        return (Optional<String>) m.invoke(resolver, results, base);
    }
}
