package org.acme.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.enterprise.context.ApplicationScoped;

import org.acme.enums.SupportedLanguage;
import org.acme.model.GenreInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@ApplicationScoped
public class GenreParser {

    private static final Logger logger = Logger.getLogger(GenreParser.class.getName());

    public List<GenreInfo> parseGenresPage(String htmlContent, String baseUrl) {
        List<GenreInfo> genres = new ArrayList<>();
        try {
            Document soup = Jsoup.parse(htmlContent);
            List<String> selectors = List.of(
                    "a[href*=\"/genres/\"]  ",
                    ".genre-list a",
                    ".category-list a",
                    ".genres a",
                    ".tags a",
                    ".genre-item a",
                    ".genre-box a",
                    ".genre-section a",
                    ".category a",
                    ".tag-cloud a",
                    "ul.genres li a",
                    "div.genres a",
                    ".genre-tag a",
                    ".genre-link",
                    "a[href*=\"/genre/\"]",
                    "a[href*=\"/category/\"]",
                    ".text-muted"
            );

            for (String selector : selectors) {
                Elements items = soup.select(selector);
                if (!items.isEmpty()) {
                    logger.fine(String.format("Found %d genres using selector: %s", items.size(), selector));
                    for (Element item : items) {
                        try {
                            String genreName = item.text().trim();
                            String url = item.attr("href");

                            if (!url.startsWith("http")) {
                                if (!url.startsWith("/")) {
                                    url = "/" + url;
                                }
                                url = "/" + SupportedLanguage.JAPANESE.getValue() + "/" + url.substring(1);
                                url = baseUrl + url;
                            }

                            if (!genreName.isEmpty() && !url.isEmpty()) {
                                Long genreId = null;
                                Matcher idMatcher = Pattern.compile("/genre/(\\d+)").matcher(url);
                                if (idMatcher.find()) {
                                    genreId = Long.parseLong(idMatcher.group(1));
                                }

                                String cleanName = genreName;
                                Matcher nameMatcher = Pattern.compile("^(.*?)\\d").matcher(genreName);
                                if (nameMatcher.find()) {
                                    cleanName = nameMatcher.group(1).trim();
                                    if (cleanName.isEmpty()) {
                                        cleanName = genreName;
                                    }
                                }

                                String code = null;
                                String[] urlParts = url.replaceAll("/$", "").split("/");
                                if (urlParts.length > 0) {
                                    code = urlParts[urlParts.length - 1];
                                    if (code.contains("?")) {
                                        code = code.split("\\?")[0];
                                    }
                                }

                                int total = 0;
                                if (!item.select(".text-muted").isEmpty()) {
                                    String totalText = item.select(".text-muted").text();
                                    total = Integer.parseInt(totalText.replaceAll("[^\\d]", ""));
                                }

                                genres.add(new GenreInfo(cleanName, url, genreId, code, genreName, total));
                            }
                        } catch (Exception e) {
                            logger.log(java.util.logging.Level.SEVERE, "Error processing genre item: " + e.getMessage(), e);
                        }
                    }
                    break; // Found genres, no need to try other selectors
                }
            }
            return genres;
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Error parsing genres page: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
