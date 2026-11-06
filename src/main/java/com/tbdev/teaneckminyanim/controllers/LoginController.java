package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkAuthenticationException;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Date;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LoginController {
    
    private final ApplicationSettingsService settingsService;
    private final MagicLinkService magicLinkService;
    private final TNMUserService userService;
    private final PasskeyCredentialService passkeyCredentialService;

    @Value("${auth.password-login.enabled:false}")
    private boolean passwordLoginEnabled;

    @Value("${auth.magic-link.enabled:true}")
    private boolean magicLinkEnabled = true;

    @Value("${auth.passkeys.enabled:true}")
    private boolean passkeysEnabled = true;
    
    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }
    
    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }

    @ModelAttribute("recaptchaEnabled")
    public boolean recaptchaEnabled() {
        return settingsService.isRecaptchaEnabled();
    }

    @ModelAttribute("recaptchaSiteKey")
    public String recaptchaSiteKey() {
        return settingsService.getRecaptchaSiteKey();
    }

    @ModelAttribute("passwordLoginEnabled")
    public boolean passwordLoginEnabled() {
        return passwordLoginEnabled;
    }

    @ModelAttribute("magicLinkEnabled")
    public boolean magicLinkEnabled() {
        return magicLinkEnabled;
    }
    
    @GetMapping("/admin/login")
    public ModelAndView login(@RequestParam(value = "error",required = false) String error, @RequestParam(value = "logout",	required = false) boolean logout) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/login");

        // Add current time for navbar display
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy h:mm a");
        Date datenow = new Date();
        String timenow = dateFormat.format(datenow);
        mv.getModel().put("timenow", timenow);

        if (logout) mv.getModel().put("logout", true);

        if (error != null && !error.isEmpty()) {
            mv.getModel().put("error", error);
        }

        return mv;
    }

    @PostMapping("/admin/login/magic-link")
    public ModelAndView requestMagicLink(@RequestParam(value = "identifier", required = false) String identifier,
                                         @RequestParam(value = "email", required = false) String email,
                                         HttpServletRequest request) {
        ModelAndView mv = login(null, false);
        MagicLinkService.MagicLinkRequestResult result = magicLinkEnabled
                ? magicLinkService.requestLoginLink(firstPresent(identifier, email), request)
                : MagicLinkService.MagicLinkRequestResult.neutral();
        mv.addObject("magicLinkMessage", result.message());
        return mv;
    }

    @PostMapping(value = "/admin/login/magic-link/request",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public MagicLinkResponse requestMagicLinkJson(@RequestBody LoginIdentifierRequest loginRequest,
                                                  HttpServletRequest request) {
        MagicLinkService.MagicLinkRequestResult result = magicLinkEnabled
                ? magicLinkService.requestLoginLink(loginRequest == null ? null : loginRequest.identifier(), request)
                : MagicLinkService.MagicLinkRequestResult.neutral();
        return new MagicLinkResponse(result.message());
    }

    @PostMapping(value = "/admin/login/auth-methods",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public LoginMethodsResponse loginMethods(@RequestBody LoginIdentifierRequest loginRequest) {
        // Keep this response account-neutral. The WebAuthn ceremony determines whether
        // a usable credential exists without exposing account state to this endpoint.
        return new LoginMethodsResponse(passkeysEnabled, magicLinkEnabled);
    }

    @GetMapping("/admin/login/magic-link/verify")
    public ModelAndView verifyMagicLink(@RequestParam(value = "token", required = false) String token,
                                        HttpServletRequest request) {
        try {
            if (!magicLinkEnabled) {
                throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
            }
            TNMUser user = magicLinkService.consumeLoginToken(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user.getUsername(),
                    null,
                    List.of(new SimpleGrantedAuthority(user.role().getName())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
            if (!passkeyCredentialService.hasPasskeys(user)) {
                return new ModelAndView("redirect:" + passkeyCredentialService.setupPromptUrl(user));
            }
            return new ModelAndView("redirect:/admin");
        } catch (MagicLinkAuthenticationException e) {
            return login(e.getMessage(), false);
        }
    }

    @GetMapping("/login")
    public ModelAndView loginShortcut(@RequestParam(value = "error",required = false) String error, @RequestParam(value = "logout",	required = false) boolean logout) {
        return login(error, logout);
    }

    private String firstPresent(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    public record LoginIdentifierRequest(String identifier) {
    }

    public record LoginMethodsResponse(boolean passkeyAvailable, boolean magicLinkAvailable) {
    }

    public record MagicLinkResponse(String message) {
    }
}
