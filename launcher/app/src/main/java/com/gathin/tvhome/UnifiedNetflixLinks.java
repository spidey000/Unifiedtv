package com.gathin.tvhome;

/** Netflix TV title ids are numeric; TMDB ids must never be substituted. */
final class UnifiedNetflixLinks {
    static boolean validTitleId(String id) {
        return id != null && id.matches("[0-9]{4,14}");
    }

    static String titleUrl(String id) {
        return validTitleId(id) ? "https://www.netflix.com/title/" + id : "";
    }

    private UnifiedNetflixLinks() {}
}
