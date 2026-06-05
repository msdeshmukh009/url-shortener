package com.urlshortener.url_shortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.enums.ResultStatus;

public record UnitShortenResponse(
        Integer index,
        ResultStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String shortCode,
        String originalUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) String error) {
    public static UnitShortenResponse success(int index, UrlShortener entity) {
        return new UnitShortenResponse(
                index,
                ResultStatus.SUCCESS,
                entity.getShortCode(),
                entity.getOriginalUrl(),
                null);
    }

    public static UnitShortenResponse failure(int index, String originalUrl, String error) {
        return new UnitShortenResponse(
                index,
                ResultStatus.FAILED,
                null,
                originalUrl,
                error);
    }
}
