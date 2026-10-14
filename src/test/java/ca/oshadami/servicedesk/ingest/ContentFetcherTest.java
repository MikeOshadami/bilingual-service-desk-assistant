package ca.oshadami.servicedesk.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentFetcherTest {

    @Test
    void keepsMainContentAndDropsSiteChrome() {
        String html = """
                <html><body>
                <header>Site header</header><nav><ul><li>Menu item</li></ul></nav>
                <main>
                  <h1>Driver's licences</h1>
                  <p>You must renew your licence every few years. This paragraph is long enough to count as content
                  for the extractor so the fallback to whole-page text is not used in this test case at all.</p>
                  <ul><li><p>Bring two pieces of ID</p></li><li>Pay the fee</li></ul>
                </main>
                <footer>Copyright</footer><script>track()</script>
                </body></html>
                """;
        String text = ContentFetcher.htmlToText(html, "https://example.org/");
        assertThat(text).contains("# Driver's licences", "You must renew", "Bring two pieces of ID", "- Pay the fee")
                .doesNotContain("Site header", "Menu item", "Copyright", "track()");
        assertThat(text.indexOf("Bring two pieces of ID")).isEqualTo(text.lastIndexOf("Bring two pieces of ID"));
    }
}
