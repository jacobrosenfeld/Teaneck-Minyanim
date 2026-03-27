package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.VersionService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequiredArgsConstructor
public class CustomErrorController implements ErrorController {

    private final ApplicationSettingsService settingsService;
    private final VersionService versionService;

    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mv = new ModelAndView("error");

        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = status != null ? Integer.parseInt(status.toString()) : 500;
        response.setStatus(statusCode);

        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            mv.addObject("errorTitle", "Page Not Found");
            mv.addObject("errorMessage", "The page you're looking for doesn't exist. It may have moved, or the URL may be incorrect.");
            mv.addObject("errorCode", "404");
        } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
            mv.addObject("errorTitle", "Access Denied");
            mv.addObject("errorMessage", "You don't have permission to view this page.");
            mv.addObject("errorCode", "403");
        } else {
            mv.addObject("errorTitle", "Something Went Wrong");
            mv.addObject("errorMessage", "An unexpected error occurred. We're looking into it.");
            mv.addObject("errorCode", String.valueOf(statusCode));
        }

        mv.addObject("siteName", settingsService.getSiteName());
        mv.addObject("supportEmail", settingsService.getSupportEmail());
        mv.addObject("appleSmartAppBannerContent", settingsService.getAppleSmartAppBannerContent());
        mv.addObject("appVersion", versionService.getVersion());

        return mv;
    }
}
