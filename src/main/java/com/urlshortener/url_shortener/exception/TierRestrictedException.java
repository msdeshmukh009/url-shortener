package com.urlshortener.url_shortener.exception;

public class TierRestrictedException extends RuntimeException {
    public TierRestrictedException(){
        super("This endpoint requires the ENTERPRISE tier. Your account is on the HOBBY tier. Please upgrade to access bulk operations.");
    }
}
