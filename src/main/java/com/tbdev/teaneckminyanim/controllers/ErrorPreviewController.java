package com.tbdev.teaneckminyanim.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ErrorPreviewController {

    @GetMapping("/test/errors/{statusCode:400|403|404|429|500|503}")
    public void previewError(@PathVariable int statusCode) {
        switch (statusCode) {
            case 400 -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Previewing 400 error page");
            case 403 -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Previewing 403 error page");
            case 404 -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Previewing 404 error page");
            case 429 -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Previewing 429 error page");
            case 500 -> throw new IllegalStateException("Previewing 500 error page");
            case 503 -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Previewing 503 error page");
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
