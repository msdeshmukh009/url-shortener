package com.urlshortener.url_shortener.dto;

import java.util.List;

import com.urlshortener.url_shortener.enums.ResultStatus;

public record BulkShortenResponse(
        Integer total,
        Integer succeeded,
        Integer failed,
        List<UnitShortenResponse> result) {
    public static BulkShortenResponse from(List<UnitShortenResponse> results) {
        int succeeded = (int) results.stream()
                .filter(r -> r.status() == ResultStatus.SUCCESS)
                .count();
        int failed = results.size() - succeeded;
        return new BulkShortenResponse(results.size(), succeeded, failed, results);
    }
}
