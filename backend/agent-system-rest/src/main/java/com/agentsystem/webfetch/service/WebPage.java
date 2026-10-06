package com.agentsystem.webfetch.service;

/**
 * A fetched, rendered web page.
 *
 * @param url       final URL after redirects
 * @param title     document title (may be empty)
 * @param markdown  page content as Markdown, already truncated to the configured cap
 * @param truncated whether {@code markdown} was cut short
 */
public record WebPage(String url, String title, String markdown, boolean truncated) {}
