package com.urlshortener.url_shortener.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.urlshortener.url_shortener.dto.ResolveOutcome;
import com.urlshortener.url_shortener.exception.InvalidPasswordException;
import com.urlshortener.url_shortener.service.UrlShortenerService;

@Controller 
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
}

    @GetMapping("/r/{shortCode}")
    public String resolve(@PathVariable String shortCode, Model model) {        
        ResolveOutcome outcome = service.checkAccess(shortCode);
        
        return switch (outcome.type()) {
            case REDIRECT -> "redirect:" + outcome.originalUrl();
            case PASSWORD_REQUIRED -> {
                model.addAttribute("shortCode", shortCode);
                yield "password-form";   // looks up templates/password-form.html
            }
        };
    }

    @PostMapping("/r/{shortCode}/unlock")
    public String unlock(
            @PathVariable String shortCode,
            @RequestParam String password,
            Model model) {
        
        try {
            String originalUrl = service.resolveWithPassword(shortCode, password);
            return "redirect:" + originalUrl;
        } catch (InvalidPasswordException e) {
            model.addAttribute("shortCode", shortCode);
            model.addAttribute("error", "Incorrect password. Please try again.");
            return "password-form";
        }
    }
}