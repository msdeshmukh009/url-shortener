package com.urlshortener.url_shortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.urlshortener.url_shortener.controller.RedirectController;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice(assignableTypes = RedirectController.class)
public class WebExceptionHandler {

    @ExceptionHandler(ShortCodeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Model model) {
        model.addAttribute("title", "Link Not Found");
        model.addAttribute("message", "The link you followed doesn't exist or has been removed.");
        return "error-page"; // renders templates/error-page.html
    }

    @ExceptionHandler(UrlExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public String handleExpired(Model model) {
        model.addAttribute("title", "Link Expired");
        model.addAttribute("message", "This link has expired and is no longer available.");
        return "error-page";
    }

    @ExceptionHandler(InvalidPasswordException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidPassword(
            HttpServletRequest request,
            Model model,
            @PathVariable(required = false) String shortCode) {
        // Re-render the password form with the error
        model.addAttribute("shortCode", extractShortCodeFromPath(request));
        model.addAttribute("error", "Incorrect password. Please try again.");
        return "password-form";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, Model model) {
        // log it
        model.addAttribute("title", "Something Went Wrong");
        model.addAttribute("message", "We encountered an error. Please try again later.");
        return "error-page";
    }

    private String extractShortCodeFromPath(HttpServletRequest request) {
        return request.getPathInfo();
    }
}
