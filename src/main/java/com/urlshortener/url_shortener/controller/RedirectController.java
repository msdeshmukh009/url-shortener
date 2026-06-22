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

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/r/{shortCode}")
    public String resolve(@PathVariable String shortCode, Model model, HttpServletResponse response) {
        ResolveOutcome outcome = service.checkAccess(shortCode);

        return switch (outcome.type()) {
            case REDIRECT -> {
                setCacheableHeaders(response);
                yield "redirect:" + outcome.originalUrl();
            }
            case PASSWORD_REQUIRED -> {
                model.addAttribute("shortCode", shortCode);
                setNoCacheHeaders(response);
                yield "password-form"; // looks up templates/password-form.html
            }
        };
    }

    @PostMapping("/r/{shortCode}/unlock")
    public String unlock(
            @PathVariable String shortCode,
            @RequestParam String password,
            Model model,
            HttpServletResponse response) {
        setNoCacheHeaders(response);
        try {
            String originalUrl = service.resolveWithPassword(shortCode, password);
            return "redirect:" + originalUrl;
        } catch (InvalidPasswordException e) {
            model.addAttribute("shortCode", shortCode);
            model.addAttribute("error", "Incorrect password. Please try again.");
            return "password-form";
        }
    }

    private void setCacheableHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "public, max-age=3600");
    }

    private void setNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
}