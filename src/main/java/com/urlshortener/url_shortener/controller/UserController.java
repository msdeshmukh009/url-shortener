package com.urlshortener.url_shortener.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.url_shortener.service.UserService;
import com.urlshortener.url_shortener.service.UserService.ThumbnailStatus;

@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/thumbnailStatus")
    public ResponseEntity<ThumbnailStatus> geThumbnailStatus(@RequestParam Integer userId) {
        ThumbnailStatus status = userService.geThumbnailStatus(userId);
        return ResponseEntity.status(HttpStatus.OK).body(status);
    }
}
