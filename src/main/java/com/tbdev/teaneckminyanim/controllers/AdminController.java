package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.*;
import com.tbdev.teaneckminyanim.security.Encrypter;
import com.tbdev.teaneckminyanim.service.*;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.minyan.MinyanDay;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.minyan.Schedule;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.tbdev.teaneckminyanim.enums.Role.ADMIN;

@Slf4j
@Controller
public class AdminController {
    @Autowired
    private TNMUserService TNMUserDAO;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private LocationService locationDAO;

    @Autowired
    private MinyanService minyanService;

    @Autowired
    private TNMSettingsService tnmsettingsDAO;

    @Autowired
    private VersionService versionService;

    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy | hh:mm aa");
    TimeZone timeZone = TimeZone.getTimeZone("America/New_York");

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().contains(new SimpleGrantedAuthority(ADMIN.getName()));
    }

    private boolean isUser() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().contains(new SimpleGrantedAuthority(Role.USER.getName())) ||
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().contains(new SimpleGrantedAuthority(ADMIN.getName()));
    }

    private TNMUser getCurrentUser() {
        return this.TNMUserDAO.findByName(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private boolean isSuperAdmin() {
        TNMUser user = getCurrentUser();
        return user.getOrganizationId() == null && user.role().equals(ADMIN);
    }

//    private List<String> getOrganizationsWithAccess() {
//        if (isAdmin()) {
//            return null;
//        } else {
//            return TNMUserDAO.getOrganizationsWithAccess(SecurityContextHolder.getContext().getAuthentication().getName());
//        }
//    }

    private void addStandardPageData(ModelAndView mv) {
        mv.addObject("user", getCurrentUser());

        Date today = new Date();
        dateFormat.setTimeZone(timeZone);
        mv.getModel().put("date", dateFormat.format(today));
        mv.getModel().put("appVersion", versionService.getVersion());
    }

    /**
     * Verifies that the current user has access to the organization in question and returns it
     */
    private Organization getOrganization(String orgId) throws AccessDeniedException {
        Optional<Organization> org = organizationService.findById(orgId);
        if (org.isEmpty()) {
            return null;
        } else {
//            verify user has user permissions for this organization
            if (!isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(org.get().getId())) {
                throw new AccessDeniedException("You do not have permission to access this organization.");
            } else {
                return org.get();
            }
        }
    }

    private Location getLocation(String locId) throws AccessDeniedException {
        Location location = locationDAO.findById(locId);
        if (location == null) {
            return null;
        } else {
//            verify user has user permissions for this organization
            if (!isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(location.getOrganizationId())) {
                throw new AccessDeniedException("You do not have permission to access this location.");
            } else {
                return location;
            }
        }
    }

    private boolean hasUserPermissions(String orgId) throws Exception {
        Optional<Organization> org = organizationService.findById(orgId);
        if (org.isEmpty()) {
            throw new Exception("Organization not found.");
        } else {
//            verify user has user permissions for this organization
            return isSuperAdmin() || getCurrentUser().getOrganizationId().equals(org.get().getId());
        }
    }

    @GetMapping("/admin/dashboard")
    public ModelAndView dashbaord() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("dashboard");

        addStandardPageData(mv);

        return mv;
    }

    @GetMapping("/admin")
    public ModelAndView admin(@RequestParam(value = "error", required = false) String error,
                              @RequestParam(value = "logout", required = false) boolean logout) {
        if (isSuperAdmin()) {
            return organizations(null, null);
        } else if (isUser()) {
            return minyanim(getCurrentUser().getOrganizationId(), null, null);
        } else {
            ModelAndView mv = new ModelAndView();
            mv.setViewName("admin/login");
            return mv;
//            throw new AccessDeniedException("You don't have permission to view this page.");
        }
    }


    @GetMapping("/admin/logout")
    public ModelAndView logout(@RequestParam(value = "error", required = false) String error) {
        return new LoginController().login(error, true);
    }

    @GetMapping("/admin/organizations")
    public ModelAndView organizations(String successMessage, String errorMessage) {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to access this page");
        }

        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/organizations");
        mv.addObject("organizations", organizationService.getAll());

        addStandardPageData(mv);

        mv.getModel().put("success", successMessage);
        mv.getModel().put("error", errorMessage);
        return mv;
    }

    @ModelAttribute("settings")
    public List<TNMSettings> settings() {
        // Load and return the settings here
        List<TNMSettings> settings = this.tnmsettingsDAO.getAll();
        Collections.sort(settings, Comparator.comparing(TNMSettings::getId)); // sort by id
        return settings;
    }

    @GetMapping("/admin/settings")
    public ModelAndView settings(String successMessage, String errorMessage) {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to access this page");
        }

        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/settings");

        List<TNMSettings> settings = this.tnmsettingsDAO.getAll();
        Collections.sort(settings, Comparator.comparing(TNMSettings::getId)); // sort by id
        mv.addObject("settings", settings);

        addStandardPageData(mv);

        mv.getModel().put("success", successMessage);
        mv.getModel().put("error", errorMessage);
        return mv;
    }


    @RequestMapping(value = "/admin/new-organization", method = RequestMethod.GET)
    public ModelAndView addOrganization(boolean success, String error, String inputErrorMessage) {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to access this page.");
        }
        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/new-organization");

        mv.getModel().put("successmessage", success ? "The organization was successfully created." : null);
        mv.getModel().put("errormessage", error);
        mv.getModel().put("inputerrormessage", inputErrorMessage);

        addStandardPageData(mv);

        return mv;
    }

    @RequestMapping(value = "/admin/create-organization", method = RequestMethod.POST)
    public ModelAndView createOrganization(@RequestParam(value = "name", required = true) String name,
                                           @RequestParam(value = "address", required = true) String address,
                                           @RequestParam(value = "username", required = true) String username,
                                           @RequestParam(value = "email", required = true) String email,
                                           @RequestParam(value = "site-url", required = false) String siteURIString,
                                           @RequestParam(value = "nusach", required = false) String nusachString,
                                           @RequestParam(value = "password", required = true) String password,
                                           @RequestParam(value = "cpassword", required = true) String cpassword,
                                           @RequestParam(value = "orgColor", required = true) String orgColor,
                                           @RequestParam(value = "url-slug", required = false) String urlSlug) {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to access this page.");
        }

        System.out.println("Validating input data...");

        if (name.isEmpty() || address.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() || cpassword.isEmpty() || orgColor.isEmpty()) {
            System.out.println("Sorry, fields cannot be left blank.");
            return addOrganization(false, "The organization could not be created. Required fields cannot be left blank.", "Sorry, required fields cannot be left blank.");
        }

        if (!password.equals(cpassword)) {
            System.out.println("Sorry, these passwords do not match.");
            return addOrganization(false, "The organization could not be created. The passwords do not match.", "Sorry, passwords do not match.");
        }

//        check if username is valid
        String usernameRegex = "^[A-Za-z]\\w{5,29}$";
        Pattern usernamePattern = Pattern.compile(usernameRegex);
        Matcher m = usernamePattern.matcher(username);
        if (!m.matches()) {
            System.out.println("Sorry, this password is not valid.");
            return addOrganization(false, "The organization could not be created. The username must be 6-30 characters, only contain letters and numbers, and start with a letter.", "Sorry, the username must be 6-30 characters, only contain letters and numbers, and start with a letter.");
        }

//        check if username already exists
        if (TNMUserDAO.findByName(username) != null) {
            System.out.println("Sorry, this username already exists.");
            return addOrganization(false, "The organization could not be created. This username already in use.","Sorry, this username is already in use.");
        }

//        check if email is valid
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        Pattern emailPattern = Pattern.compile(emailRegex);
        Matcher m2 = emailPattern.matcher(email);
        if (!m2.matches()) {
            System.out.println("Sorry, this email address is not valid.");
            return addOrganization(false, "The organization could not be created. This email address is invalid.","Sorry, this email address is invalid.");
        }

//        check if url string is valid
        URI siteURI = null;
        if (siteURIString != null && !siteURIString.isEmpty()) {
            try {
                siteURI = new URI(siteURIString);
            } catch (URISyntaxException e) {
                e.printStackTrace();
                System.out.println("Sorry, this site URI is not valid.");
                return addOrganization(false, "The organization could not be created. This site URI is invalid.", "Sorry, this site URI is invalid.");
            }
        }

//        check if password is valid
        String passwordRegex = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{8,}$";
        Pattern passwordPattern = Pattern.compile(passwordRegex);
        Matcher m3 = passwordPattern.matcher(password);
        if (!m3.matches()) {
            System.out.println("Sorry, this password is not valid.");
            return addOrganization(false, "The organization could not be created. The password must be at least 8 characters, contain at least one upper and lower case letters and one number.", "The organization could not be created. The password must be at least 8 characters, contain at least one upper and lower case letters and one number.");
        }

        System.out.println("Creating organization...");

        Nusach nusach = Nusach.fromString(nusachString);
        if (nusach == null) {
            System.out.println("Sorry, this nusach couldn't be validated.");
            return addOrganization(false, "The organization could not be created.", "Sorry, the organization could not be created.");
        }

        Organization organization = Organization.builder()
                .id(IDGenerator.generateID('O'))
                .name(name)
                .address(address)
                .websiteURI(siteURI)
                .nusach(nusach)
                .orgColor(orgColor)
                .urlSlug(urlSlug)
                .build();

        // Ensure organization has a slug (generate from name if not provided)
        organizationService.ensureSlug(organization);

        if  (this.organizationService.save(organization)) {
            System.out.println("Organization created successfully.");
            System.out.println("Creating account for organization...");

            TNMUser user = TNMUser.builder()
                    .id(IDGenerator.generateID('A'))
                    .username(username)
                    .email(email)
                    .encryptedPassword(Encrypter.encrytedPassword(password))
                    .organizationId(organization.getId())
                    .roleId(ADMIN.getId())
                    .build();
            if (this.TNMUserDAO.save(user)) {
                return addOrganization(true, null, null);
            } else {
                System.out.println("Account creation failed. Deleting organization from database...");
//                TODO: REMOVE ORGANIZATION FROM DATABASE
                if (this.organizationService.delete(organization)) {
                    System.out.println("Organization deleted successfully.");
                } else {
                    System.out.println("Organization deletion failed.");
                }

                return addOrganization(false, "Sorry, there was an error creating the account.", null);
            }
        } else {
            System.out.println("Organization creation failed.");
            return addOrganization(false,"Sorry, there was an error creating the organization.", null);
        }
    }

    @RequestMapping(value = "/admin/accounts", method = RequestMethod.GET)
    public ModelAndView accounts(String successMessage, String errorMessage) {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You are not authorized to access this page.");
        }

        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/accounts");

        List<TNMUser> users = this.TNMUserDAO.getAll();
        mv.addObject("accounts", users);

        Map<String, String> organizationNames = new HashMap<>();
        for (TNMUser user : users) {
            if (user.getOrganizationId()!= null) {
                Optional<Organization> organization = this.organizationService.findById(user.getOrganizationId());
                String organizationDisplayName = organization.isEmpty() ? "" : organization.get().getName();
                organizationNames.put(user.getId(), organizationDisplayName);
            }
        }
        mv.addObject("organizationNames", organizationNames);

        addStandardPageData(mv);

        mv.getModel().put("successmessage", successMessage);
        mv.getModel().put("errormessage", errorMessage);

        return mv;
    }

    @GetMapping("/admin/account")
    public ModelAndView account(@RequestParam(value = "id", required = true) String id,
                                String successMessage,
                                String errorMessage, String changePasswordError) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("account");
        if (isSuperAdmin()) {
            TNMUser queriedUser = this.TNMUserDAO.findById(id);
            System.out.println("Queried user: " + queriedUser);
            mv.addObject("queriedaccount", queriedUser);

            if (queriedUser.isSuperAdmin() && !queriedUser.getId().equals(getCurrentUser().getId())) {
                throw new AccessDeniedException("You are not authorized to view this account.");
            }

            if (!queriedUser.isSuperAdmin()) {
                Optional<Organization> associatedOrganization = this.organizationService.findById(queriedUser.getOrganizationId());
                mv.addObject("associatedorganization", associatedOrganization.orElseGet(Organization::new));
            }
        } else if (isAdmin()) {
            TNMUser user = getCurrentUser();
            TNMUser queriedUser = this.TNMUserDAO.findById(id);

            if (user != null && user.getOrganizationId().equals(queriedUser.getOrganizationId()) && !(queriedUser.isAdmin() && !queriedUser.getId().equals(user.getId()))) {

                mv.addObject("queriedaccount", queriedUser);

                Optional<Organization> associatedOrganization = this.organizationService.findById(queriedUser.getOrganizationId());
                mv.addObject("associatedorganization", associatedOrganization.orElseGet(Organization::new));
            } else {
                throw new AccessDeniedException("You are not authorized to view this account.");
            }
        } else if (isUser()) {
            TNMUser user = getCurrentUser();

            if (!user.getId().equals(id)) {
                throw new AccessDeniedException("You are not authorized to view this account.");
            }

            TNMUser queriedUser = this.TNMUserDAO.findById(id);
            System.out.println("Queried user: " + queriedUser);

            if (user.getId().equals(queriedUser.getId())) {
                mv.setViewName("account");

                mv.addObject("queriedaccount", queriedUser);

                Optional<Organization> associatedOrganization = this.organizationService.findById(queriedUser.getOrganizationId());
                mv.addObject("associatedorganization", associatedOrganization.orElseGet(Organization::new));
            } else {
                throw new AccessDeniedException("You are not authorized to view this account.");
            }
        } else {
            throw new AccessDeniedException("You are not authorized to view this account.");
        }

        addStandardPageData(mv);

        mv.addObject("changePasswordError", changePasswordError);
        mv.addObject("successMessage", successMessage);
        mv.addObject("mainErrorMessage", errorMessage);

        return mv;
    }

    @PostMapping("/admin/accounts/{accountId}/changepassword")
    public ModelAndView changeAccountPassword(@PathVariable String accountId, @RequestParam(value = "password", required = true) String password, @RequestParam(value = "cpassword", required = true) String cpassword) {
        if (!password.equals(cpassword)) {
            return account(accountId, null, null, "Sorry, the passwords don't match.");
        }

        TNMUser targetUser = TNMUserDAO.findById(accountId);
        if (targetUser == null) {
            return account(accountId, null, null, "Sorry, we ran into an unexpected error.");
        }

        if (!getCurrentUser().isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(targetUser.getOrganizationId())) {
            throw new AccessDeniedException("You don't have permission to interact with this account.");
        }

        if (!getCurrentUser().isSuperAdmin() && !getCurrentUser().isAdmin() && !getCurrentUser().getId().equals(targetUser.getId())) {
            throw new AccessDeniedException("You don't have permission to interact with this account.");
        }

        if (targetUser.isSuperAdmin() && !getCurrentUser().getId().equals(targetUser.getId())) {
            throw new AccessDeniedException("You don't have permission to interact with this account.");
        }

        //        check if password is valid
        String passwordRegex = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{8,}$";
        Pattern passwordPattern = Pattern.compile(passwordRegex);
        Matcher m3 = passwordPattern.matcher(password);
        if (!m3.matches()) {
            System.out.println("Sorry, this password is not valid.");
            return account(accountId, null, null, "The organization could not be created. The password must be at least 8 characters, contain at least one upper and lower case letters and one number.");
        }

        try {
            TNMUserDAO.update(
                    TNMUser.builder()
                            .id(targetUser.getId())
                            .email(targetUser.getUsername())
                            .encryptedPassword(targetUser.getEncryptedPassword())
                            .organizationId(targetUser.getOrganizationId())
                            .roleId(targetUser.getRoleId())
                            .build());

            return account(accountId, "Successfully updated the account password.", null, null);
        } catch (Exception e) {
            return account(accountId, null, null, "Sorry, we ran into an unexpected error.");
        }
    }

    @GetMapping("/admin/organization")
    public ModelAndView organization(@RequestParam(value = "id", required = false) String id,
                                     String mainSuccessMessage,
                                     String mainErrorMessage,
                                     String updateErrorMessage,
                                     String addAccountErrorMessage) throws Exception {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/organization");

        if (mainSuccessMessage != null && !mainSuccessMessage.isEmpty()) {
            mv.addObject("mainSuccess", mainSuccessMessage);
        }

        if (mainErrorMessage != null && !mainErrorMessage.isEmpty()) {
            mv.addObject("mainError", mainErrorMessage);
        }

        if (updateErrorMessage != null && !updateErrorMessage.isEmpty()) {
            mv.addObject("updateError", updateErrorMessage);
        }

        if (addAccountErrorMessage != null && !addAccountErrorMessage.isEmpty()) {
            mv.addObject("addAccountError", addAccountErrorMessage);
        }

//        check permissions
        if (isAdmin()) {
//                find organization for id
            Optional<Organization> organization = this.organizationService.findById(id);
            if (organization.isEmpty()) {
                System.out.println("Organization not found.");
//                    TODO: HANDLE ERROR CORRECTLY
                throw new Exception("Organization not found.");
            } else {
                mv.getModel().put("organization", organization.get());
            }

            List<TNMUser> associatedUsers = this.organizationService.getUsersForOrganization(organization.get());
            mv.addObject("associatedusers", associatedUsers);
        } else if (isUser()) {
//              check if user is associated with organization
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            TNMUser user = this.TNMUserDAO.findByName(username);
            String associatedOrganizationId = user.getOrganizationId();
            if (!associatedOrganizationId.equals(id)) {
                System.out.println("You do not have permission to view this organization.");
                throw new AccessDeniedException("You do not have permission to view this organization.");
            } else {
//                find organization for id
                Optional<Organization> organization = this.organizationService.findById(id);
                if (organization.isEmpty()) {
                    System.out.println("Organization not found.");
//                    TODO: HANDLE ERROR CORRECTLY
                    throw new Exception("Organization not found.");
                } else {
                    mv.getModel().put("organization", organization);
                }


                List<TNMUser> associatedUsers = this.organizationService.getUsersForOrganization(organization.get());
                mv.addObject("associatedusers", associatedUsers);
            }
        }

        addStandardPageData(mv);

        return mv;
    }

    @RequestMapping(value = "/admin/update-organization", method = RequestMethod.POST)
    public ModelAndView updateOrganization(@RequestParam(value = "id", required = true) String id,
                                           @RequestParam(value = "name", required = true) String name,
                                           @RequestParam(value = "address", required = false) String address,
                                           @RequestParam(value = "site-url", required = false) String siteURIString,
                                           @RequestParam(value = "nusach", required = true) String nusachString,
                                           @RequestParam(value = "orgColor", required = true) String orgColor,
                                           @RequestParam(value = "url-slug", required = false) String urlSlug,
                                           @RequestParam(value = "calendar", required = false) String calendar,
                                           @RequestParam(value = "useScrapedCalendar", required = false) Boolean useScrapedCalendar) throws Exception {

//        validate input
        if (name == null || name.isEmpty()) {
            return organization(id, null, null, "Organization name cannot be empty.", null);
        }

        URI siteURI = null;
        if (siteURIString != null && !siteURIString.isEmpty()) {
            try {
                siteURI = new URI(siteURIString);
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return organization(id, null, null, "Invalid website URL.", null);
            }
        }

        Nusach nusach = Nusach.fromString(nusachString);
        if (nusach == null) {
            return organization(id, null, null, "Invalid nusach type.", null);
        }

        Organization organization = Organization.builder()
                .id(id)
                .name(name)
                .address(address)
                .websiteURI(siteURI)
                .nusach(nusach)
                .orgColor(orgColor)
                .urlSlug(urlSlug)
                .calendar(calendar)
                .useScrapedCalendar(useScrapedCalendar != null ? useScrapedCalendar : false)
                .build();

        // Ensure organization has a slug (generate from name if not provided)
        organizationService.ensureSlug(organization);

//        check permissions
        if (isAdmin()) {
            if (this.organizationService.update(organization)) {
                // Redirect to the organization page after a successful update
                RedirectView redirectView = new RedirectView("/admin/organization?id=" + id, true);
                return new ModelAndView(redirectView);
            } else {
                // Handle update failure
                return organization(id, null, null, "Sorry, the update failed.", null);
            }
        } else if (isUser()) {
            // ... (Permissions check and organization update, similar to admin case)

            if (this.organizationService.update(organization)) {
                // Redirect to the organization page after a successful update
                RedirectView redirectView = new RedirectView("/admin/organization?id=" + id, true);
                return new ModelAndView(redirectView);
            } else {
                // Handle update failure
                return organization(id, null, "Sorry, the update failed.", null, null);
            }
        } else {
            throw new AccessDeniedException("You do not have permission to view this organization.");
        }
    }

    @RequestMapping(value = "/admin/delete-organization")
    public ModelAndView deleteOrganization(@RequestParam(value = "id", required = true) String id) throws Exception {
        if (isSuperAdmin()) {
//            get organization and check if it exists
            Optional<Organization> organization = this.organizationService.findById(id);
            if (organization.isEmpty()) {
                System.out.println("Organization does not exist. Failed to delete.");
                return organizations(null, "Sorry, the organization could not be deleted.");
            } else {
                if (this.organizationService.delete(organization.get())) {
                    System.out.println("Organization deleted successfully.");
                    return organizations("Successfully deleted the organization.", null);
                } else {
                    System.out.println("Organization delete failed.");
                    return organizations(null, "Sorry, the organization could not be deleted.");
                }
            }
        } /*else if (SecurityContextHolder.getContext().getAuthentication().getAuthorities().contains(new SimpleGrantedAuthority(Role.USER.getName()))) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            TNMUser user = this.TNMUserDAO.findByName(username);
            String associatedOrganizationId = user.getOrganizationId();
            if (!associatedOrganizationId.equals(id)) {
                System.out.println("You do not have permission to view this organization.");
                throw new AccessDeniedException("You do not have permission to view this organization.");
            } else {
//                get organization and check if it exists
                Organization organization = this.organizationDAO.findById(id);
                if (organization != null) {
                    if (this.organizationDAO.delete(organization)) {
                        System.out.println("Organization deleted successfully.");
                        return organizations("Successfully deleted the organization.", null);
                    } else {
                        System.out.println("Organization delete failed.");
                        return organizations(null, "Sorry, the delete failed.");
                    }
                } else {
                    System.out.println("Organization does not exist. Failed to delete.");
                    return organizations(null, "Sorry, the organization could not be deleted.");
                }
            }
        }*/ else {
            throw new AccessDeniedException("You do not have permission to delete an organization.");
        }
    }

    @RequestMapping(value = "/admin/delete-account")
    public ModelAndView deleteAccount(@RequestParam(value = "id", required = true) String id) throws Exception {
        if (isSuperAdmin()) {
//            get organization and check if it exists
            TNMUser account = this.TNMUserDAO.findById(id);
            if (account != null) {
                if (this.TNMUserDAO.delete(account)) {
                    System.out.println("Account deleted successfully.");
                    return accounts("Successfully deleted the account.", null);
                } else {
                    System.out.println("Account delete failed.");
                    return accounts(null, "Sorry, the account could not be deleted.");
                }
            } else {
                System.out.println("Account does not exist. Failed to delete.");
                return accounts(null, "Sorry, the account could not be deleted.");
            }
        } else if (isAdmin()) {
            TNMUser account = this.TNMUserDAO.findById(id);
            if (!getCurrentUser().getOrganizationId().equals(account.getOrganizationId())) {
                System.out.println("You do not have permission to view this organization.");
                throw new AccessDeniedException("You do not have permission to view this organization.");
            } else {
                if (this.TNMUserDAO.delete(account)) {
                    System.out.println("Account deleted successfully.");
                    return accounts("Successfully deleted the account.", null);
                } else {
                    System.out.println("Account delete failed.");
                    return accounts(null, "Sorry, the account could not be deleted.");
                }
            }
        } else {
            throw new AccessDeniedException("You do not have permission to delete this account.");
        }
    }

    @RequestMapping(value = "/admin/create-account")
    public ModelAndView createAccount(
            @RequestParam(value = "username", required = true) String username,
            @RequestParam(value = "email", required = true) String email,
            @RequestParam(value = "password", required = true) String password,
            @RequestParam(value = "cpassword") String cpassword,
            @RequestParam(value = "oid", required = true) String organizationId,
            @RequestParam(value = "r", required = false) String roleInital
    ) throws Exception {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("You do not have permission to create an account.");
        }

//            validate input
        System.out.println("Validating input data...");

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || cpassword.isEmpty()) {
            System.out.println("Sorry, fields cannot be left blank.");
            return organization(organizationId, null, null, null, "Sorry, fields cannot be left blank.");
        }

        if (!password.equals(cpassword)) {
            System.out.println("Sorry, passwords do not match.");
            return organization(organizationId, null, null, null, "Sorry, passwords do not match.");
        }

        String usernameRegex = "^[A-Za-z]\\w{5,29}$";
        Pattern usernamePatter = Pattern.compile(usernameRegex);
        Matcher m = usernamePatter.matcher(username);
        if (!m.matches()) {
            System.out.println("Sorry, this username is not valid.");
            return organization(organizationId, null, null, null,"Sorry, the username must be 6-30 characters, only contain letters and numbers, and start with a letter.");
        }

//        check if username already exists
        if (TNMUserDAO.findByName(username) != null) {
            System.out.println("Sorry, this username already exists.");
            return organization(organizationId, null, null, null,"Sorry, this username already exists.");
        }


//        check if email is valid
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        Pattern emailPattern = Pattern.compile(emailRegex);
        Matcher m2 = emailPattern.matcher(email);
        if (!m2.matches()) {
            System.out.println("Sorry, this email address is not valid.");
            return organization(organizationId, null, null, null,"Sorry, this email address is invalid.");
        }


//        check if password is valid
        String passwordRegex = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{8,}$";
        Pattern passwordPattern = Pattern.compile(passwordRegex);
        Matcher m3 = passwordPattern.matcher(password);
        if (!m3.matches()) {
            System.out.println("Sorry, this password is not valid.");
            return organization(organizationId, null, null, null,"The organization could not be created. The password must be at least 8 characters, contain at least one upper and lower case letters and one number.");
        }

        Role role;
        if (roleInital.toLowerCase().equals("a")) {
            role = ADMIN;
        } else if (roleInital.toLowerCase().equals("u") && organizationId != null) {
            role = Role.USER;
        } else if (organizationId != null) {
            role = Role.USER;
        } else {
            System.out.println("No organization selected.");
            return organization(organizationId, null, null, null,"Sorry, an error occurred.");
        }

        if (role == ADMIN) {
            if (isAdmin()) {
                System.out.println("Creating account...");

                TNMUser user = TNMUser.builder()
                        .id(IDGenerator.generateID('A'))
                        .username(username.toLowerCase())
                        .email(email.toLowerCase())
                        .encryptedPassword(Encrypter.encrytedPassword(password))
                        .organizationId(organizationId)
                        .roleId(role.getId())
                        .build();

                if (this.TNMUserDAO.save(user)) {
                    return organization(organizationId, "Successfully created a new account with username '" + user.getUsername() + ".'", null, null, null);
                } else {
                    System.out.println("Account creation failed.");
                    return organization(organizationId, null, null, null,"Sorry, an error occurred.");
                }
            } else {
                throw new AccessDeniedException("You do not have permission to create an administrator account.");
            }
        } else if (Objects.equals(role.getName(), Role.USER.getName())) {
            if (isAdmin()) {
                System.out.println("Creating account...");

                TNMUser user = TNMUser.builder()
                        .id(IDGenerator.generateID('A')) // Generate the ID here
                        .username(username.toLowerCase())
                        .email(email.toLowerCase())
                        .encryptedPassword(Encrypter.encrytedPassword(password))
                        .organizationId(organizationId)
                        .roleId(role.getId())
                        .build();
                if (this.TNMUserDAO.save(user)) {
                    return organization(organizationId, "Successfully created a new account with username '" + user.getUsername() + ".'", null, null, null);
                } else {
                    System.out.println("Account creation failed.");
                    return organization(organizationId, null, null, null,"Sorry, an error occurred.");
                }
            } else if (isUser()) {
//                    check if user is in the same organization
                if (organizationId != null) {
                    if (TNMUserDAO.findByName(username).getId().equals(organizationId)) {
                        System.out.println("Creating account...");

                        TNMUser user = TNMUser.builder()
                                .id(IDGenerator.generateID('A')) // Generate the ID here
                                .username(username.toLowerCase())
                                .email(email.toLowerCase())
                                .encryptedPassword(Encrypter.encrytedPassword(password))
                                .organizationId(organizationId)
                                .roleId(role.getId())
                                .build();
                        if (this.TNMUserDAO.save(user)) {
                            return organization(organizationId, "Successfully created a new account with username '" + user.getUsername() + ".'", null, null, null);
                        } else {
                            System.out.println("Account creation failed.");
                            return organization(organizationId, null, null, null,"Sorry, an error occurred.");
                        }
                    } else {
                        throw new AccessDeniedException("You do not have permission to create an account for this organization.");
                    }
                } else {
                    System.out.println("No organization selected.");
                    return organization(organizationId, null, null, null,"Sorry, an error occurred.");
                }
            } else {
                throw new AccessDeniedException("You do not have permission to create an account.");
            }
        } else {
            System.out.println("Error: The role is null.");
            return organization(organizationId, null, null, null,"Sorry, an error occurred.");
        }
    }

    @RequestMapping(value = "/admin/update-account", method = RequestMethod.POST)
    public ModelAndView updateAccount(
            @RequestParam(value = "id", required = true) String id,
            @RequestParam(value = "username", required = true) String newUsername,
            @RequestParam(value = "email", required = true) String newEmail,
            @RequestParam(value = "privileges", required = false) int roleId
    ) {
        System.out.println("IM IN THE FUNCTION");
        TNMUser userToUpdate = TNMUserDAO.findById(id);
        TNMUser currentUser = getCurrentUser();

        if (!currentUser.isSuperAdmin()) {
            if (!currentUser.getOrganizationId().equals(userToUpdate.getOrganizationId())) {
                System.out.println("Exiting at break 1");
                throw new AccessDeniedException("You do not have permission to update this account.");
            }
        }

//        if (!isSuperAdmin() && !(isAdmin() && currentUser.getOrganizationId().equals(userToUpdate.getOrganizationId())) && !(currentUser.getId().equals(userToUpdate.getId()))) {
//            throw new AccessDeniedException("You do not have permission to update this account.");
//        }

        Role newRole;
        if (roleId == 1) {
            newRole = Role.ADMIN;
        } else if (roleId == 2) {
            newRole = Role.USER;
        } else {
            newRole = userToUpdate.role();
        }

        if (!isAdmin() && !newRole.equals(userToUpdate.role())) {
            System.out.println("Exiting at break 2");
            throw new AccessDeniedException("You do not have permission to update this information.");
        }

        System.out.printf("IDS: %s %s " + userToUpdate.getId().equals(currentUser.getId()), currentUser.getId(), userToUpdate.getId());

//        TODO: DECIDE ABOUT CONTROL OF SUPER ADMIN STATUSES
        if (isSuperAdmin() && (!userToUpdate.isSuperAdmin() || userToUpdate.getId().equals(currentUser.getId()))) {

            TNMUser updatedUser = TNMUser.builder()
                    .id(id)
                    .username(newUsername.toLowerCase())
                    .email(newEmail.toLowerCase())
                    .encryptedPassword(userToUpdate.getEncryptedPassword())
                    .organizationId(userToUpdate.getOrganizationId())
                    .roleId(newRole.getId())
                    .build();
            if (TNMUserDAO.update(updatedUser)) {
                return account(id,"Successfully updated account with username '" + updatedUser.getUsername() + "'.", null, null);
            } else {
                return account(id,null, "Sorry, an error occurred. The account could not be updated.", null);
            }
        } else if (isAdmin() && !userToUpdate.isSuperAdmin()) {
            TNMUser updatedUser = TNMUser.builder()
                    .id(id)
                    .username(newUsername.toLowerCase())
                    .email(newEmail.toLowerCase())
                    .encryptedPassword(userToUpdate.getEncryptedPassword())
                    .organizationId(userToUpdate.getOrganizationId())
                    .roleId(newRole.getId())
                    .build();
            if (TNMUserDAO.update(updatedUser)) {
                return account(id,"Successfully updated account with username '" + updatedUser.getUsername() + "'.", null, null);
            } else {
                return account(id,null, "Sorry, an error occurred. The account could not be updated.", null);
            }
        } else if (!isAdmin() && userToUpdate.getId().equals(getCurrentUser().getId())) {
            TNMUser updatedUser = TNMUser.builder()
                    .username(newUsername.toLowerCase())
                    .email(newEmail.toLowerCase())
                    .encryptedPassword(userToUpdate.getEncryptedPassword())
                    .organizationId(userToUpdate.getOrganizationId())
                    .roleId(userToUpdate.getRoleId())
                    .build();
            if (TNMUserDAO.update(updatedUser)) {
                return account(id,"Successfully updated account with username '" + updatedUser.getUsername() + "'.", null, null);
            } else {
                return account(id,null, "Sorry, an error occurred. The account could not be updated.", null);
            }
        } else {
            throw new AccessDeniedException("You do not have permission to update this information.");
        }
    }

    @RequestMapping(value = "/admin/update-settings", method = RequestMethod.POST)
    public ModelAndView updateSettings(
            @RequestParam(value = "setting", required = false) String setting,
            @RequestParam(value = "enabled", required = false) String newEnabled,
            @RequestParam(value = "id", required = true) String id,
            @RequestParam(value = "text", required = false) String newText,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "expirationDate", required = false) String expirationDate,
            @RequestParam(value = "maxDisplays", required = false) Integer maxDisplays
    ) {
        TNMSettings settingtoUpdate = tnmsettingsDAO.findById(id);
        
        // Validate setting exists
        if (settingtoUpdate == null) {
            return settings("Setting not found with id: " + id, null);
        }

        // Validate expiration date format if provided
        if (expirationDate != null && !expirationDate.isEmpty()) {
            if (!expirationDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return settings(null, "Invalid expiration date format. Please use YYYY-MM-DD format.");
            }
        }

        // Validate maxDisplays range if provided
        if (maxDisplays != null) {
            if (maxDisplays < 1 || maxDisplays > 100) {
                return settings(null, "Max displays must be between 1 and 100.");
            }
        }

        TNMSettings settings = new TNMSettings();
        settings.setId(settingtoUpdate.getId());
        settings.setSetting(setting);
        settings.setEnabled(newEnabled);
        settings.setText(newText);
        settings.setType(type);
        settings.setExpirationDate(expirationDate);
        settings.setMaxDisplays(maxDisplays);
        
        if (tnmsettingsDAO.update(settings)) {
            // return settings ("Successfully updated setting with name '" + settings.getSetting() + "'.", null);
            RedirectView redirectView = new RedirectView("/admin/settings", true);
            return new ModelAndView(redirectView);
        } else {
            return settings(null, "Sorry, an error occurred. The setting could not be updated.");
        }
    }

    @RequestMapping(value = "/admin/{oid}/locations", method = RequestMethod.GET)
    public ModelAndView locations(@PathVariable(value = "oid") String oid, String successMessage, String errorMessage) {
        String oidToUse;
        if (isSuperAdmin()) {
            oidToUse = oid;
        } else {
            oidToUse = getCurrentUser().getOrganizationId();
        }

        ModelAndView mv = new ModelAndView("admin/locations");
        mv.addObject("locations", locationDAO.findMatching(oidToUse));

        TNMUser currentUser = getCurrentUser();
        mv.addObject("user", currentUser);

        Optional<Organization> organization = organizationService.findById(oidToUse);
        mv.addObject("organization", organization.orElseGet(Organization::new));

        addStandardPageData(mv);

        mv.addObject("successmessage", successMessage);
        mv.addObject("errormessage", errorMessage);

        return mv;
    }

    @RequestMapping(value = "/admin/create-location")
    public ModelAndView createLocation(@RequestParam(value = "name", required = true) String name, @RequestParam(value = "oid", required = true) String organizationId) {
        if (!isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(organizationId)) {
            throw new AccessDeniedException("You do not have permission to create a location for this organization.");
        }

        if (name.isEmpty()) {
            return locations(null, null, "Sorry, an error occurred. The location could not be created.");
        }

        Location location = new Location(name, organizationId);
        if (locationDAO.save(location)) {
            return locations(organizationId,"Successfully created location '" + location.getName() + ".'", null);
        } else {
            return locations(organizationId,null, "Sorry, an error occurred. The location could not be created.");
        }
    }

    @RequestMapping(value = "/admin/update-location", method = RequestMethod.POST)
    public ModelAndView updateLocation(
            @RequestParam(value = "id", required = true) String id,
            @RequestParam(value = "name", required = true) String newName) {
        Location locationToUpdate = locationDAO.findById(id);
        if (!isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(locationToUpdate.getOrganizationId())) {
            throw new AccessDeniedException("You do not have permission to update a location for this organization.");
        }

        String organizationId = locationToUpdate.getOrganizationId();

        if (newName.isEmpty()) {
            return locations(organizationId, null, "Sorry, an error occurred. The location could not be updated.");
        }

        Location location = new Location(id, newName, locationToUpdate.getOrganizationId());
        if (locationDAO.update(location)) {
            return locations(organizationId, "Successfully updated location '" + location.getName() + ".'", null);
        } else {
            return locations(organizationId, null, "Sorry, an error occurred. The location could not be updated.");
        }
    }


    @RequestMapping(value = "/admin/delete-location")
    public ModelAndView deleteLocation(@RequestParam(value = "id", required = true) String id) {
        Location locationToDelete = locationDAO.findById(id);
        String organizationId = locationToDelete.getOrganizationId();
        if (!isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(locationToDelete.getOrganizationId())) {
            throw new AccessDeniedException("You do not have permission to delete a location for this organization.");
        }

        if (locationDAO.delete(locationToDelete)) {
            return locations(organizationId, "Successfully deleted location '" + locationToDelete.getName() + ".'", null);
        } else {
            return locations(organizationId, null, "Sorry, an error occurred. The location could not be deleted.");
        }
    }

    @RequestMapping(value = "/admin/{organizationId}/minyanim")
    public ModelAndView minyanim(@PathVariable String organizationId, String successMessage, String errorMessage) {
        ModelAndView mv = new ModelAndView("minyanschedule");

        String oidToUse;
        if (isSuperAdmin()) {
            if (organizationId == null) {
                throw new IllegalArgumentException("You must specify an organization ID.");
            } else {
                oidToUse = organizationId;
            }
        } else if (isUser()) {
            oidToUse = getCurrentUser().getOrganizationId();
        } else {
            throw new AccessDeniedException("You do not have permission to view this page.");
        }

        List<Minyan> minyanim = minyanService.findMatching(oidToUse);
//        minyanim.stream().filter(m -> m.getType() == MinyanType.SHACHARIS);

//        get elements from list that are shacharis
//        List<Minyan> shacharisMinyanim = new ArrayList<>();
//        for (Minyan m : minyanim) {
//            if (m.getType().equals("shacharis")) {
//                shacharisMinyanim.add(m);
//            }
//        }
        List<Minyan> shacharisMinyanim = minyanim.stream().filter(m -> m.getType().equals(MinyanType.SHACHARIS)).collect(Collectors.toList());
        mv.addObject("shacharisminyanim", shacharisMinyanim);
        Map<String, HashMap<MinyanDay, MinyanTime>> shacharisTimes = new HashMap<>();
        for (Minyan m : shacharisMinyanim) {
            shacharisTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }

        List<Minyan> minchaMinyanim = minyanim.stream().filter(m -> m.getType().equals(MinyanType.MINCHA)).collect(Collectors.toList());
        mv.addObject("minchaminyanim", minchaMinyanim);
        Map<String, HashMap<MinyanDay, MinyanTime>> minchaTimes = new HashMap<>();
        for (Minyan m : minchaMinyanim) {
            minchaTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }

        List<Minyan> maarivMinyanim = minyanim.stream().filter(m -> m.getType().equals(MinyanType.MAARIV)).collect(Collectors.toList());
        mv.addObject("maarivminyanim", maarivMinyanim);
        Map<String, HashMap<MinyanDay, MinyanTime>> maarivTimes = new HashMap<>();
        for (Minyan m : maarivMinyanim) {
            maarivTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }

        List<Minyan> selichosMinyanim = minyanim.stream().filter(m -> m.getType().equals(MinyanType.SELICHOS)).collect(Collectors.toList());
        mv.addObject("selichosminyanim", selichosMinyanim);
        Map<String, HashMap<MinyanDay, MinyanTime>> selichosTimes = new HashMap<>();
        for (Minyan m : selichosMinyanim) {
            selichosTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }

        List<Minyan> megilaMinyanim = minyanim.stream().filter(m -> m.getType().equals(MinyanType.MEGILA_READING)).collect(Collectors.toList());
        mv.addObject("megilaminyanim", megilaMinyanim);
        Map<String, HashMap<MinyanDay, MinyanTime>> megilaTimes = new HashMap<>();
        for (Minyan m : megilaMinyanim) {
            megilaTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }

        mv.addObject("shacharistimes", shacharisTimes);
        mv.addObject("minchatimes", minchaTimes);
        mv.addObject("maarivtimes", maarivTimes);
        mv.addObject("selichostimes", selichosTimes);
        mv.addObject("megilatimes", megilaTimes);

        mv.addObject("Day", MinyanDay.class);

        Map<String, String> locationNames = new HashMap<>();
        for (Minyan minyan : minyanim) {
            Location location = this.locationDAO.findById(minyan.getLocationId());
//            TODO: DONT JUST SHOW EMPTY STRING
            String locationDisplayName = location == null ? "" : location.getName();
            locationNames.put(minyan.getId(), locationDisplayName);
        }
        mv.addObject("locationnames", locationNames);

        mv.addObject("organization", organizationService.findById(oidToUse).orElse(null));

        mv.addObject("successmessage", successMessage);
        mv.addObject("errormessage", errorMessage);

        addStandardPageData(mv);

        return mv;
    }

    @RequestMapping(value = "/admin/{organizationId}/minyanim/printable")
    public ModelAndView printableMinyanim(@PathVariable String organizationId, 
                                        @RequestParam(value = "types", required = false) String[] selectedTypes,
                                        @RequestParam(value = "locations", required = false) String[] selectedLocations) {
        ModelAndView mv = new ModelAndView("printable-schedule");

        String oidToUse;
        if (isSuperAdmin()) {
            if (organizationId == null) {
                throw new IllegalArgumentException("You must specify an organization ID.");
            } else {
                oidToUse = organizationId;
            }
        } else if (isUser()) {
            oidToUse = getCurrentUser().getOrganizationId();
        } else {
            throw new AccessDeniedException("You do not have permission to view this page.");
        }

        List<Minyan> allMinyanim = minyanService.findMatching(oidToUse);
        List<Minyan> filteredMinyanim = allMinyanim.stream()
            .filter(m -> m.isEnabled()) // Only show enabled minyanim
            .collect(Collectors.toList());

        // Apply type filters if specified
        if (selectedTypes != null && selectedTypes.length > 0) {
            Set<String> typeSet = Arrays.stream(selectedTypes).collect(Collectors.toSet());
            filteredMinyanim = filteredMinyanim.stream()
                .filter(m -> typeSet.contains(m.getType().toString()))
                .collect(Collectors.toList());
        }

        // Apply location filters if specified
        if (selectedLocations != null && selectedLocations.length > 0) {
            Set<String> locationSet = Arrays.stream(selectedLocations).collect(Collectors.toSet());
            filteredMinyanim = filteredMinyanim.stream()
                .filter(m -> locationSet.contains(m.getLocationId()))
                .collect(Collectors.toList());
        }

        // Group minyanim by type for organized display
        List<Minyan> shacharisMinyanim = filteredMinyanim.stream()
            .filter(m -> m.getType().equals(MinyanType.SHACHARIS))
            .collect(Collectors.toList());
        List<Minyan> minchaMinyanim = filteredMinyanim.stream()
            .filter(m -> m.getType().equals(MinyanType.MINCHA))
            .collect(Collectors.toList());
        List<Minyan> maarivMinyanim = filteredMinyanim.stream()
            .filter(m -> m.getType().equals(MinyanType.MAARIV))
            .collect(Collectors.toList());

        mv.addObject("shacharisminyanim", shacharisMinyanim);
        mv.addObject("minchaminyanim", minchaMinyanim);
        mv.addObject("maarivminyanim", maarivMinyanim);

        // Create time maps for each type
        Map<String, HashMap<MinyanDay, MinyanTime>> shacharisTimes = new HashMap<>();
        for (Minyan m : shacharisMinyanim) {
            shacharisTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }
        Map<String, HashMap<MinyanDay, MinyanTime>> minchaTimes = new HashMap<>();
        for (Minyan m : minchaMinyanim) {
            minchaTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }
        Map<String, HashMap<MinyanDay, MinyanTime>> maarivTimes = new HashMap<>();
        for (Minyan m : maarivMinyanim) {
            maarivTimes.put(m.getId(), m.getSchedule().getMappedSchedule());
        }

        mv.addObject("shacharistimes", shacharisTimes);
        mv.addObject("minchatimes", minchaTimes);
        mv.addObject("maarivtimes", maarivTimes);
        mv.addObject("Day", MinyanDay.class);

        // Add location names
        Map<String, String> locationNames = new HashMap<>();
        for (Minyan minyan : filteredMinyanim) {
            Location location = this.locationDAO.findById(minyan.getLocationId());
            String locationDisplayName = location == null ? "" : location.getName();
            locationNames.put(minyan.getId(), locationDisplayName);
        }
        mv.addObject("locationnames", locationNames);

        // Add organization and filter options for the filter form
        mv.addObject("organization", organizationService.findById(oidToUse).orElse(null));
        mv.addObject("alllocations", locationDAO.findMatching(oidToUse));
        mv.addObject("selectedTypes", selectedTypes);
        mv.addObject("selectedLocations", selectedLocations);

        addStandardPageData(mv);
        return mv;
    }

    //    enable and disable pages
    @RequestMapping(value = "/admin/minyanim/{id}/enable")
    public ModelAndView enableMinyan(@PathVariable String id, @RequestParam(value = "rd", required = false) String rd) {
        Minyan minyan = minyanService.findById(id);
        if (minyan == null) {
            throw new IllegalArgumentException("Invalid minyan ID.");
        }

//        ensure that user is admin or minyan is in their organization
        if (!isSuperAdmin() && !minyan.getOrganizationId().equals(getCurrentUser().getOrganizationId())) {
            throw new AccessDeniedException("You do not have permission to enable this minyan.");
        }

        minyan.setEnabled(true);
        minyanService.update(minyan);

        ModelAndView mv = new ModelAndView();
        if (rd != null) {
            mv.setViewName("redirect:" + rd);
        } else {
            mv.setViewName("redirect:/admin/" + minyan.getOrganizationId() + "/minyanim");
        }
        return mv;
    }

    @RequestMapping(value = "/admin/minyanim/{id}/disable")
    public ModelAndView disableMinyan(@PathVariable String id, @RequestParam(value = "rd", required = false) String rd) {
        Minyan minyan = minyanService.findById(id);
        if (minyan == null) {
            throw new IllegalArgumentException("Invalid minyan ID.");
        }

//        ensure that user is admin or minyan is in their organization
        if (!isSuperAdmin() && !minyan.getOrganizationId().equals(getCurrentUser().getOrganizationId())) {
            throw new AccessDeniedException("You do not have permission to disable this minyan.");
        }

        minyan.setEnabled(false);
        minyanService.update(minyan);

        ModelAndView mv = new ModelAndView();
        if (rd != null) {
            mv.setViewName("redirect:" + rd);
        } else {
            mv.setViewName("redirect:/admin/" + minyan.getOrganizationId() + "/minyanim");
        }
        return mv;
    }

    @RequestMapping(value="/admin/{orgId}/minyanim/new")
    public ModelAndView newMinyan(@PathVariable String orgId) throws AccessDeniedException {
        Organization org = this.getOrganization(orgId);

        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/minyanim/new");

//        add locations to mv
        mv.addObject("locations", locationDAO.findMatching(orgId));
        mv.addObject("organization", org);
        addStandardPageData(mv);

        return mv;
    }

    @RequestMapping(value="/admin/{orgId}/minyanim/create")
    public ModelAndView createMinyan(@PathVariable String orgId,
                                     @RequestParam(value = "type", required = false) String type,
                                     @RequestParam(value = "location", required = false) String locationId,
                                     @RequestParam(value = "sunday-time-type", required = true) String sundayTimeType,
                                     @RequestParam(value = "sunday-fixed-time", required = false) String sundayTimeString,
                                     @RequestParam(value = "sunday-zman", required = false) String sundayZman,
                                     @RequestParam(value = "sunday-zman-offset", required = false) Integer sundayZmanOffset,
                                     @RequestParam(value = "monday-time-type", required = true) String mondayTimeType,
                                     @RequestParam(value = "monday-fixed-time", required = false) String mondayTimeString,
                                     @RequestParam(value = "monday-zman", required = false) String mondayZman,
                                     @RequestParam(value = "monday-zman-offset", required = false) Integer mondayZmanOffset,
                                     @RequestParam(value = "tuesday-time-type", required = true) String tuesdayTimeType,
                                     @RequestParam(value = "tuesday-fixed-time", required = false) String tuesdayTimeString,
                                     @RequestParam(value = "tuesday-zman", required = false) String tuesdayZman,
                                     @RequestParam(value = "tuesday-zman-offset", required = false) Integer tuesdayZmanOffset,
                                     @RequestParam(value = "wednesday-time-type", required = true) String wednesdayTimeType,
                                     @RequestParam(value = "wednesday-fixed-time", required = false) String wednesdayTimeString,
                                     @RequestParam(value = "wednesday-zman", required = false) String wednesdayZman,
                                     @RequestParam(value = "wednesday-zman-offset", required = false) Integer wednesdayZmanOffset,
                                     @RequestParam(value = "thursday-time-type", required = true) String thursdayTimeType,
                                     @RequestParam(value = "thursday-fixed-time", required = false) String thursdayTimeString,
                                     @RequestParam(value = "thursday-zman", required = false) String thursdayZman,
                                     @RequestParam(value = "thursday-zman-offset", required = false) Integer thursdayZmanOffset,
                                     @RequestParam(value = "friday-time-type", required = true) String fridayTimeType,
                                     @RequestParam(value = "friday-fixed-time", required = false) String fridayTimeString,
                                     @RequestParam(value = "friday-zman", required = false) String fridayZman,
                                     @RequestParam(value = "friday-zman-offset", required = false) Integer fridayZmanOffset,
                                     @RequestParam(value = "shabbos-time-type", required = true) String shabbosTimeType,
                                     @RequestParam(value = "shabbos-fixed-time", required = false) String shabbosTimeString,
                                     @RequestParam(value = "shabbos-zman", required = false) String shabbosZman,
                                     @RequestParam(value = "shabbos-zman-offset", required = false) Integer shabbosZmanOffset,
                                     @RequestParam(value = "rc-time-type", required = true) String rcTimeType,
                                     @RequestParam(value = "rc-fixed-time", required = false) String rcTimeString,
                                     @RequestParam(value = "rc-zman", required = false) String rcZman,
                                     @RequestParam(value = "rc-zman-offset", required = false) Integer rcZmanOffset,
                                     @RequestParam(value = "yt-time-type", required = true) String ytTimeType,
                                     @RequestParam(value = "yt-fixed-time", required = false) String ytTimeString,
                                     @RequestParam(value = "yt-zman", required = false) String ytZman,
                                     @RequestParam(value = "yt-zman-offset", required = false) Integer ytZmanOffset,
                                     @RequestParam(value = "chanuka-time-type", required = true) String chanukaTimeType,
                                     @RequestParam(value = "chanuka-fixed-time", required = false) String chanukaTimeString,
                                     @RequestParam(value = "chanuka-zman", required = false) String chanukaZman,
                                     @RequestParam(value = "chanuka-zman-offset", required = false) Integer chanukaZmanOffset,
                                     @RequestParam(value = "rcc-time-type", required = true) String rccTimeType,
                                     @RequestParam(value = "rcc-fixed-time", required = false) String rccTimeString,
                                     @RequestParam(value = "rcc-zman", required = false) String rccZman,
                                     @RequestParam(value = "rcc-zman-offset", required = false) Integer rccZmanOffset,
                                     @RequestParam(value = "nusach", required = false) String nusachString,
                                     @RequestParam(value = "notes", required = false) String notes,
                                     @RequestParam(value = "whatsapp", required = false) String whatsapp,
                                     @RequestParam(value = "enabled", required = false) String enabledString,
                                     @RequestParam(value = "return", required = false) boolean returnAndRefill) throws Exception {

//        get and verify organization
        Organization organization = getOrganization(orgId);

        ModelAndView mv = newMinyan(orgId);

//        verify minyan type
        MinyanType minyanType = MinyanType.fromString(type);
        if (minyanType == null) {
            mv.addObject("errormessage", "Sorry, there was an error creating the minyan. Please try again. (M01)");
            return mv;
        }

//        System.out.println("Minyan type: " + type);

//        get and verify location
        Location location = getLocation(locationId);

        //        create minyan times
        MinyanTime sundayTime = MinyanTime.fromFormData(sundayTimeType, sundayTimeString, sundayZman, sundayZmanOffset);
        MinyanTime mondayTime = MinyanTime.fromFormData(mondayTimeType, mondayTimeString, mondayZman, mondayZmanOffset);
        MinyanTime tuesdayTime = MinyanTime.fromFormData(tuesdayTimeType, tuesdayTimeString, tuesdayZman, tuesdayZmanOffset);
        MinyanTime wednesdayTime = MinyanTime.fromFormData(wednesdayTimeType, wednesdayTimeString, wednesdayZman, wednesdayZmanOffset);
        MinyanTime thursdayTime = MinyanTime.fromFormData(thursdayTimeType, thursdayTimeString, thursdayZman, thursdayZmanOffset);
        MinyanTime fridayTime = MinyanTime.fromFormData(fridayTimeType, fridayTimeString, fridayZman, fridayZmanOffset);
        MinyanTime shabbosTime = MinyanTime.fromFormData(shabbosTimeType, shabbosTimeString, shabbosZman, shabbosZmanOffset);
        MinyanTime rcTime = MinyanTime.fromFormData(rcTimeType, rcTimeString, rcZman, rcZmanOffset);
        MinyanTime ytTime = MinyanTime.fromFormData(ytTimeType, ytTimeString, ytZman, ytZmanOffset);
        MinyanTime chanukaTime = MinyanTime.fromFormData(chanukaTimeType, chanukaTimeString, chanukaZman, chanukaZmanOffset);
        MinyanTime rccTime = MinyanTime.fromFormData(rccTimeType, rccTimeString, rccZman, rccZmanOffset);

        Schedule schedule = new Schedule(sundayTime, mondayTime, tuesdayTime, wednesdayTime, thursdayTime, fridayTime, shabbosTime, ytTime, rcTime, chanukaTime, rccTime);

//        validate nusach
        Nusach nusach = Nusach.fromString(nusachString.toUpperCase());
        if (nusach == null) {
            mv.addObject("errormessage", "Invalid or missing Nusach value. Please provide a valid Nusach. (M02)");
            return mv;
        }

        String orgnazationColor = organization.getOrgColor();

        boolean enabled;
        if (enabledString != null && !enabledString.isEmpty()) {
            if (enabledString.equals("on")) {
                enabled = true;
            } else {
                enabled = Boolean.parseBoolean(enabledString);
            }
        } else {
            enabled = false;
        }
//        System.out.println("Enabled: " + enabled);

        Minyan minyan = new Minyan(organization, minyanType, location, schedule, notes, nusach, enabled, orgnazationColor, whatsapp);

        try {
            minyanService.save(minyan);

            mv.addObject("successmessage", "You successfully created a minyan. Click <a href='/admin/" + orgId + "/minyanim/'>here</a> to return to the minyan schedule. Click <a href=\"#\" onclick='branch()'>here</a> to fill in the fields like the last minyan.");

            mv.addObject("branchMinyan", minyan);

            return mv;
        } catch (Exception e) {
            e.printStackTrace();

            mv.addObject("errormessage", "Sorry, there was an error saving the minyan. (M03)");
            return mv;
        }
    }

    @RequestMapping(value = "/admin/minyanim/{id}/view", method = RequestMethod.GET)
    public ModelAndView viewMinyan(@PathVariable("id") String id) throws Exception {
        ModelAndView mv = new ModelAndView("admin/minyanim/update");
        Minyan minyan = minyanService.findById(id);
//        authenticate user has permission to edit minyan
        Optional<Organization> minyanOrganization = organizationService.findById(minyan.getOrganizationId());
        if (!isSuperAdmin() && minyanOrganization.isPresent() && !getCurrentUser().getOrganizationId().equals(minyanOrganization.get().getId())) {
            throw new AccessDeniedException("You do not have permission to edit this minyan.");
        }
        if (minyanOrganization.isPresent()) {
            mv.addObject("minyan", minyan);
            mv.addObject("organization", minyanOrganization.get());
            mv.addObject("locations", locationDAO.findMatching(minyanOrganization.get().getId()));
        }
        addStandardPageData(mv);
        return mv;
    }

    @RequestMapping(value = "/admin/{organizationId}/minyanim/{minyanId}/update", method = RequestMethod.POST)
    public ModelAndView updateMinyan(@PathVariable("organizationId") String organizationId,
                                     @PathVariable("minyanId") String minyanId,
                                     @RequestParam(value = "type", required = false) String type,
                                     @RequestParam(value = "location", required = false) String locationId,
                                     @RequestParam(value = "sunday-time-type", required = true) String sundayTimeType,
                                     @RequestParam(value = "sunday-fixed-time", required = false) String sundayTimeString,
                                     @RequestParam(value = "sunday-zman", required = false) String sundayZman,
                                     @RequestParam(value = "sunday-zman-offset", required = false) Integer sundayZmanOffset,
                                     @RequestParam(value = "monday-time-type", required = true) String mondayTimeType,
                                     @RequestParam(value = "monday-fixed-time", required = false) String mondayTimeString,
                                     @RequestParam(value = "monday-zman", required = false) String mondayZman,
                                     @RequestParam(value = "monday-zman-offset", required = false) Integer mondayZmanOffset,
                                     @RequestParam(value = "tuesday-time-type", required = true) String tuesdayTimeType,
                                     @RequestParam(value = "tuesday-fixed-time", required = false) String tuesdayTimeString,
                                     @RequestParam(value = "tuesday-zman", required = false) String tuesdayZman,
                                     @RequestParam(value = "tuesday-zman-offset", required = false) Integer tuesdayZmanOffset,
                                     @RequestParam(value = "wednesday-time-type", required = true) String wednesdayTimeType,
                                     @RequestParam(value = "wednesday-fixed-time", required = false) String wednesdayTimeString,
                                     @RequestParam(value = "wednesday-zman", required = false) String wednesdayZman,
                                     @RequestParam(value = "wednesday-zman-offset", required = false) Integer wednesdayZmanOffset,
                                     @RequestParam(value = "thursday-time-type", required = true) String thursdayTimeType,
                                     @RequestParam(value = "thursday-fixed-time", required = false) String thursdayTimeString,
                                     @RequestParam(value = "thursday-zman", required = false) String thursdayZman,
                                     @RequestParam(value = "thursday-zman-offset", required = false) Integer thursdayZmanOffset,
                                     @RequestParam(value = "friday-time-type", required = true) String fridayTimeType,
                                     @RequestParam(value = "friday-fixed-time", required = false) String fridayTimeString,
                                     @RequestParam(value = "friday-zman", required = false) String fridayZman,
                                     @RequestParam(value = "friday-zman-offset", required = false) Integer fridayZmanOffset,
                                     @RequestParam(value = "shabbos-time-type", required = true) String shabbosTimeType,
                                     @RequestParam(value = "shabbos-fixed-time", required = false) String shabbosTimeString,
                                     @RequestParam(value = "shabbos-zman", required = false) String shabbosZman,
                                     @RequestParam(value = "shabbos-zman-offset", required = false) Integer shabbosZmanOffset,
                                     @RequestParam(value = "rc-time-type", required = true) String rcTimeType,
                                     @RequestParam(value = "rc-fixed-time", required = false) String rcTimeString,
                                     @RequestParam(value = "rc-zman", required = false) String rcZman,
                                     @RequestParam(value = "rc-zman-offset", required = false) Integer rcZmanOffset,
                                     @RequestParam(value = "yt-time-type", required = true) String ytTimeType,
                                     @RequestParam(value = "yt-fixed-time", required = false) String ytTimeString,
                                     @RequestParam(value = "yt-zman", required = false) String ytZman,
                                     @RequestParam(value = "yt-zman-offset", required = false) Integer ytZmanOffset,
                                     @RequestParam(value = "chanuka-time-type", required = true) String chanukaTimeType,
                                     @RequestParam(value = "chanuka-fixed-time", required = false) String chanukaTimeString,
                                     @RequestParam(value = "chanuka-zman", required = false) String chanukaZman,
                                     @RequestParam(value = "chanuka-zman-offset", required = false) Integer chanukaZmanOffset,
                                     @RequestParam(value = "rcc-time-type", required = true) String rccTimeType,
                                     @RequestParam(value = "rcc-fixed-time", required = false) String rccTimeString,
                                     @RequestParam(value = "rcc-zman", required = false) String rccZman,
                                     @RequestParam(value = "rcc-zman-offset", required = false) Integer rccZmanOffset,
                                     @RequestParam(value = "nusach", required = false) String nusachString,
                                     @RequestParam(value = "notes", required = false) String notes,
                                     @RequestParam(value = "whatsapp", required = false) String whatsapp,
                                     @RequestParam(value = "enabled", required = false) String enabledString) throws Exception {

//        print starting message
        System.out.println("Updating minyan with id " + minyanId);

//        confirm user has access to organization
        Optional<Organization> organization = organizationService.findById(organizationId);
        if (organization.isEmpty()) {
            throw new Exception("Organization not found.");
        } else {
//            verify user has permission to create minyan for this organization
            if (!isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(organization.get().getId())) {
                throw new AccessDeniedException("You do not have permission to create a minyan for this organization.");
            }
        }

        Minyan oldMinyan = minyanService.findById(minyanId);
        if (oldMinyan == null) {
            throw new Exception("Minyan not found.");
        }

        String organizationColor = organization.get().getOrgColor();

//        verify minyan type
        MinyanType minyanType = MinyanType.fromString(type);
        if (minyanType == null) {
            ModelAndView vm = viewMinyan(minyanId);
            vm.addObject("errormessage", "Sorry, there was an error creating the minyan. Please try again. (M01)");
            return vm;
        }

        System.out.println("Minyan type: " + minyanType);

//        get and verify location
        Location location = locationDAO.findById(locationId);
        if (location != null) {
            if (!location.getOrganizationId().equals(organization.get().getId())) {
                throw new AccessDeniedException("You do not have permission to create a minyan using this location.");
            }
        }

//        create minyan times
        MinyanTime sundayTime = MinyanTime.fromFormData(sundayTimeType, sundayTimeString, sundayZman, sundayZmanOffset);
        MinyanTime mondayTime = MinyanTime.fromFormData(mondayTimeType, mondayTimeString, mondayZman, mondayZmanOffset);
        MinyanTime tuesdayTime = MinyanTime.fromFormData(tuesdayTimeType, tuesdayTimeString, tuesdayZman, tuesdayZmanOffset);
        MinyanTime wednesdayTime = MinyanTime.fromFormData(wednesdayTimeType, wednesdayTimeString, wednesdayZman, wednesdayZmanOffset);
        MinyanTime thursdayTime = MinyanTime.fromFormData(thursdayTimeType, thursdayTimeString, thursdayZman, thursdayZmanOffset);
        MinyanTime fridayTime = MinyanTime.fromFormData(fridayTimeType, fridayTimeString, fridayZman, fridayZmanOffset);
        MinyanTime shabbosTime = MinyanTime.fromFormData(shabbosTimeType, shabbosTimeString, shabbosZman, shabbosZmanOffset);
        MinyanTime rcTime = MinyanTime.fromFormData(rcTimeType, rcTimeString, rcZman, rcZmanOffset);
        MinyanTime ytTime = MinyanTime.fromFormData(ytTimeType, ytTimeString, ytZman, ytZmanOffset);
        MinyanTime chanukaTime = MinyanTime.fromFormData(chanukaTimeType, chanukaTimeString, chanukaZman, chanukaZmanOffset);
        MinyanTime rccTime = MinyanTime.fromFormData(rccTimeType, rccTimeString, rccZman, rccZmanOffset);

        System.out.println("Sunday minyan time: " + sundayTime);
        System.out.println("Monday minyan time: " + mondayTime);
        System.out.println("Tuesday minyan time: " + tuesdayTime);
        System.out.println("Wednesday minyan time: " + wednesdayTime);
        System.out.println("Thursday minyan time: " + thursdayTime);
        System.out.println("Friday minyan time: " + fridayTime);
        System.out.println("Shabbos minyan time: " + shabbosTime);
        System.out.println("Rosh Chodesh minyan time: " + rcTime);
        System.out.println("Yom Tov minyan time: " + ytTime);
        System.out.println("Chanuka minyan time: " + chanukaTime);
        System.out.println("RCC minyan time: " + rccTime);

//        validate nusach
        Nusach nusach = Nusach.fromString(nusachString);
        if (nusach == null) {
            ModelAndView mv = viewMinyan(minyanId);
            mv.addObject("errormessage", "Sorry, there was an error updating the minyan. Please try again. (M02)");
            return mv;
        }
        System.out.println("Nusach: " + nusach);

        System.out.println("Notes: " + notes);

        System.out.println("WhatsApp: " + whatsapp);

        Schedule schedule = new Schedule(sundayTime, mondayTime, tuesdayTime, wednesdayTime, thursdayTime, fridayTime, shabbosTime, rcTime, ytTime, chanukaTime, rccTime);

        Minyan updatedMinyan = new Minyan(oldMinyan.getId(), organization.get(), minyanType, location, schedule, notes, nusach, oldMinyan.isEnabled(), organizationColor, whatsapp);

        try {
            minyanService.update(updatedMinyan);

            ModelAndView mv = viewMinyan(minyanId);
            mv.addObject("successmessage", "The minyan was successfully updated. Click <a href='/admin/" + organizationId + "/minyanim/'>here</a> to return to the minyan schedule.");
            return mv;
        } catch (Exception e) {
            e.printStackTrace();

            ModelAndView mv = viewMinyan(minyanId);
            mv.addObject("errormessage", "Sorry, there was an error saving the minyan. (M03)");
            return mv;
        }
    }

    @RequestMapping(value = "/admin/{organizationId}/minyanim/{minyanId}/delete", method = RequestMethod.GET)
    public ModelAndView deleteMinyan(@PathVariable("organizationId") String organizationId, @PathVariable("minyanId") String minyanId) throws Exception {
        System.out.println("Deleting minyan with id " + minyanId);

        Minyan minyan = minyanService.findById(minyanId);
        if (minyan == null) {
            throw new Exception("Minyan not found.");
        } else {
            if (!isSuperAdmin() && !getCurrentUser().getOrganizationId().equals(minyan.getOrganizationId())) {
                throw new AccessDeniedException("You do not have permission to delete this minyan.");
            }

            try {
                minyanService.delete(minyan);
                return minyanim(organizationId, "The minyan was successfully deleted.", null);
            } catch (Exception e) {
                e.printStackTrace();
                throw new Exception("Sorry, there was an error deleting the minyan.");
            }
        }
    }

    // ==================== Calendar Import Endpoints ====================

    /**
     * Manual trigger for calendar import for a specific organization
     */
    @PostMapping("/admin/{orgId}/calendar/import")
    public ModelAndView triggerCalendarImport(@PathVariable String orgId) {
        if (!isAdmin()) {
            throw new AccessDeniedException("You do not have permission to access this page.");
        }

        try {
            Organization org = getOrganization(orgId);
            if (org == null) {
                ModelAndView mv = new ModelAndView("redirect:/admin/" + orgId + "/calendar-entries");
                mv.addObject("errorMessage", "Organization not found");
                return mv;
            }

            // Check if calendar URL is configured
            if (org.getCalendar() == null || org.getCalendar().trim().isEmpty()) {
                ModelAndView mv = new ModelAndView("redirect:/admin/" + orgId + "/calendar-entries");
                mv.addObject("errorMessage", "No calendar URL configured");
                return mv;
            }

            // Trigger import
            com.tbdev.teaneckminyanim.service.calendar.CalendarImportService importService = 
                    applicationContext.getBean(com.tbdev.teaneckminyanim.service.calendar.CalendarImportService.class);
            
            com.tbdev.teaneckminyanim.service.calendar.CalendarImportService.ImportResult result = 
                    importService.importCalendarForOrganization(orgId);

            ModelAndView mv = new ModelAndView("redirect:/admin/" + orgId + "/calendar-entries");
            
            if (result.success) {
                mv.addObject("successMessage", 
                        String.format("Import successful: %d new, %d updated, %d duplicates skipped",
                                result.newEntries, result.updatedEntries, result.duplicatesSkipped));
            } else {
                mv.addObject("errorMessage", "Import failed: " + result.errorMessage);
            }
            
            return mv;

        } catch (Exception e) {
            ModelAndView mv = new ModelAndView("redirect:/admin/" + orgId + "/calendar-entries");
            mv.addObject("errorMessage", "Import error: " + e.getMessage());
            return mv;
        }
    }

    /**
     * View and manage imported calendar entries for an organization
     */
    @GetMapping("/admin/{orgId}/calendar-entries")
    public ModelAndView viewCalendarEntries(
            @PathVariable String orgId,
            @RequestParam(required = false) String successMessage,
            @RequestParam(required = false) String errorMessage,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String filterClassification,
            @RequestParam(required = false) String filterEnabled,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "false") Boolean showNonMinyan) {
        
        if (!isAdmin()) {
            throw new AccessDeniedException("You do not have permission to access this page.");
        }

        ModelAndView mv = new ModelAndView("admin/calendar-entries");
        addStandardPageData(mv);

        try {
            Organization org = getOrganization(orgId);
            if (org == null) {
                mv.setViewName("redirect:/admin");
                return mv;
            }

            mv.addObject("organization", org);

            // Get repository bean
            com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository entryRepo = 
                    applicationContext.getBean(com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository.class);

            // Build sort
            org.springframework.data.domain.Sort sort = buildSort(sortBy, sortDir);

            // Get entries with filtering
            List<com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry> entries = 
                    getFilteredEntries(entryRepo, orgId, filterClassification, filterEnabled, 
                                     searchText, startDate, endDate, showNonMinyan, sort);

            // Calculate statistics
            long totalEntries = entryRepo.countByOrganizationId(orgId);
            long enabledCount = entries.stream().filter(e -> e.isEnabled()).count();
            long disabledCount = entries.stream().filter(e -> !e.isEnabled()).count();
            long minyanCount = entries.stream()
                    .filter(e -> e.getClassification() != null && e.getClassification().isMinyan())
                    .count();
            long nonMinyanCount = entries.stream()
                    .filter(e -> e.getClassification() != null && e.getClassification().isNonMinyan())
                    .count();

            mv.addObject("entries", entries);
            mv.addObject("totalEntries", totalEntries);
            mv.addObject("filteredCount", entries.size());
            mv.addObject("enabledCount", enabledCount);
            mv.addObject("disabledCount", disabledCount);
            mv.addObject("minyanCount", minyanCount);
            mv.addObject("nonMinyanCount", nonMinyanCount);

            // Get locations for this organization for the dropdown
            LocationService locationService = applicationContext.getBean(LocationService.class);
            List<Location> locations = locationService.findMatching(orgId);
            mv.addObject("locations", locations);

            // Add filter/sort parameters back to view for persistence
            mv.addObject("sortBy", sortBy != null ? sortBy : "date");
            mv.addObject("sortDir", sortDir != null ? sortDir : "desc");
            mv.addObject("filterClassification", filterClassification);
            mv.addObject("filterEnabled", filterEnabled);
            mv.addObject("searchText", searchText);
            mv.addObject("startDate", startDate);
            mv.addObject("endDate", endDate);
            mv.addObject("showNonMinyan", showNonMinyan);

            if (successMessage != null) {
                mv.addObject("successMessage", successMessage);
            }
            if (errorMessage != null) {
                mv.addObject("errorMessage", errorMessage);
            }

        } catch (Exception e) {
            mv.addObject("errorMessage", "Error loading calendar entries: " + e.getMessage());
            log.error("Error loading calendar entries for org {}", orgId, e);
        }

        return mv;
    }

    /**
     * Build Sort object from request parameters
     */
    private org.springframework.data.domain.Sort buildSort(String sortBy, String sortDir) {
        String sortField = sortBy != null ? sortBy : "date";
        org.springframework.data.domain.Sort.Direction direction = 
                "asc".equalsIgnoreCase(sortDir) ? 
                org.springframework.data.domain.Sort.Direction.ASC : 
                org.springframework.data.domain.Sort.Direction.DESC;

        // Map sort field names to entity field names
        switch (sortField) {
            case "time":
                sortField = "startTime";
                break;
            case "title":
                sortField = "title";
                break;
            case "type":
                sortField = "classification";
                break;
            case "enabled":
                sortField = "enabled";
                break;
            case "importedAt":
                sortField = "importedAt";
                break;
            default:
                sortField = "date";
        }

        return org.springframework.data.domain.Sort.by(direction, sortField)
                .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "startTime"));
    }

    /**
     * Get filtered entries based on request parameters
     */
    private List<com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry> getFilteredEntries(
            com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository entryRepo,
            String orgId,
            String filterClassification,
            String filterEnabled,
            String searchText,
            String startDate,
            String endDate,
            Boolean showNonMinyan,
            org.springframework.data.domain.Sort sort) {
        
        List<com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry> entries;

        // Apply text search if provided
        if (searchText != null && !searchText.trim().isEmpty()) {
            entries = entryRepo.searchByText(orgId, searchText.trim(), sort);
        }
        // Apply date range filter if provided
        else if (startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
            try {
                java.time.LocalDate start = java.time.LocalDate.parse(startDate);
                java.time.LocalDate end = java.time.LocalDate.parse(endDate);
                
                com.tbdev.teaneckminyanim.minyan.MinyanType classification = null;
                if (filterClassification != null && !filterClassification.isEmpty()) {
                    classification = com.tbdev.teaneckminyanim.minyan.MinyanType.fromString(filterClassification);
                }
                
                entries = entryRepo.findInRangeWithClassification(orgId, start, end, classification, sort);
            } catch (Exception e) {
                log.warn("Error parsing date range: {} to {}", startDate, endDate, e);
                entries = entryRepo.findByOrganizationId(orgId, sort);
            }
        }
        // Apply classification filter
        else if (filterClassification != null && !filterClassification.isEmpty()) {
            try {
                com.tbdev.teaneckminyanim.minyan.MinyanType classification = 
                        com.tbdev.teaneckminyanim.minyan.MinyanType.fromString(filterClassification);
                entries = entryRepo.findByOrganizationIdAndClassification(orgId, classification, sort);
            } catch (Exception e) {
                log.warn("Error parsing classification: {}", filterClassification, e);
                entries = entryRepo.findByOrganizationId(orgId, sort);
            }
        }
        // Apply enabled filter
        else if (filterEnabled != null && !filterEnabled.isEmpty()) {
            boolean enabled = "true".equalsIgnoreCase(filterEnabled);
            entries = entryRepo.findByOrganizationIdAndEnabled(orgId, enabled, sort);
        }
        // Default: get all entries
        else {
            entries = entryRepo.findByOrganizationId(orgId, sort);
        }

        // Filter out non-minyan entries by default unless showNonMinyan is true
        if (!showNonMinyan) {
            entries = entries.stream()
                    .filter(e -> e.getClassification() == null || 
                                !e.getClassification().isNonMinyan())
                    .collect(java.util.stream.Collectors.toList());
        }

        return entries;
    }

    /**
     * Toggle enabled/disabled status for a calendar entry
     */
    @PostMapping("/admin/{orgId}/calendar-entries/{entryId}/toggle")
    public RedirectView toggleCalendarEntry(@PathVariable String orgId, @PathVariable Long entryId) {
        if (!isAdmin()) {
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }

        try {
            Organization org = getOrganization(orgId);
            if (org == null) {
                return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Organization+not+found");
            }

            com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository entryRepo = 
                    applicationContext.getBean(com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository.class);

            Optional<com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry> entryOpt = 
                    entryRepo.findById(entryId);

            if (entryOpt.isPresent()) {
                com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry entry = entryOpt.get();
                
                // Verify entry belongs to this organization
                if (!entry.getOrganizationId().equals(orgId)) {
                    throw new AccessDeniedException("Entry does not belong to this organization");
                }

                // Toggle enabled status
                entry.setEnabled(!entry.isEnabled());
                entryRepo.save(entry);

                return new RedirectView("/admin/" + orgId + "/calendar-entries?successMessage=Entry+status+updated");
            } else {
                return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Entry+not+found");
            }

        } catch (Exception e) {
            return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=" + e.getMessage());
        }
    }

    /**
     * Update location for a calendar entry
     */
    @PostMapping("/admin/{orgId}/calendar-entries/{entryId}/update-location")
    public RedirectView updateEntryLocation(@PathVariable String orgId, 
                                           @PathVariable Long entryId,
                                           @RequestParam(required = false) String locationId) {
        if (!isAdmin()) {
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }

        try {
            Organization org = getOrganization(orgId);
            if (org == null) {
                return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Organization+not+found");
            }

            com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository entryRepo = 
                    applicationContext.getBean(com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository.class);

            Optional<com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry> entryOpt = 
                    entryRepo.findById(entryId);

            if (entryOpt.isPresent()) {
                com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry entry = entryOpt.get();
                
                // Verify entry belongs to this organization
                if (!entry.getOrganizationId().equals(orgId)) {
                    throw new AccessDeniedException("Entry does not belong to this organization");
                }

                // Update location
                if (locationId != null && !locationId.isEmpty()) {
                    LocationService locationService = applicationContext.getBean(LocationService.class);
                    Location location = locationService.findById(locationId);
                    if (location != null) {
                        entry.setLocation(location.getName());
                        entry.setLocationManuallyEdited(true);
                        entry.setManuallyEditedBy(getCurrentUser().getUsername());
                        entry.setManuallyEditedAt(java.time.LocalDateTime.now());
                        entryRepo.save(entry);
                        
                        return new RedirectView("/admin/" + orgId + "/calendar-entries?successMessage=Location+updated");
                    } else {
                        return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Location+not+found");
                    }
                } else {
                    // Clear location
                    entry.setLocation(null);
                    entry.setLocationManuallyEdited(true);
                    entry.setManuallyEditedBy(getCurrentUser().getUsername());
                    entry.setManuallyEditedAt(java.time.LocalDateTime.now());
                    entryRepo.save(entry);
                    
                    return new RedirectView("/admin/" + orgId + "/calendar-entries?successMessage=Location+cleared");
                }
            } else {
                return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Entry+not+found");
            }

        } catch (Exception e) {
            log.error("Error updating location for entry {}: {}", entryId, e.getMessage(), e);
            return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=" + e.getMessage());
        }
    }

    /**
     * Reimport all calendar entries for an organization (override all existing data)
     */
    @PostMapping("/admin/{orgId}/calendar/reimport-all")
    public RedirectView reimportAllCalendarEntries(@PathVariable String orgId) {
        if (!isAdmin()) {
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }

        try {
            Organization org = getOrganization(orgId);
            if (org == null) {
                return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Organization+not+found");
            }

            // Delete all existing entries for this organization
            com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository entryRepo = 
                    applicationContext.getBean(com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository.class);
            
            List<com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry> existingEntries = 
                    entryRepo.findByOrganizationIdOrderByDateDesc(orgId);
            
            entryRepo.deleteAll(existingEntries);
            log.info("Deleted {} existing entries for organization {}", existingEntries.size(), orgId);

            // Trigger fresh import
            com.tbdev.teaneckminyanim.service.calendar.CalendarImportService importService = 
                    applicationContext.getBean(com.tbdev.teaneckminyanim.service.calendar.CalendarImportService.class);
            
            com.tbdev.teaneckminyanim.service.calendar.CalendarImportService.ImportResult result = 
                    importService.importCalendarForOrganization(orgId);

            if (result.success) {
                return new RedirectView("/admin/" + orgId + "/calendar-entries?successMessage=Reimport+complete:+" 
                        + result.newEntries + "+entries+imported");
            } else {
                return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Reimport+failed:+" 
                        + result.errorMessage);
            }

        } catch (Exception e) {
            log.error("Error reimporting calendar for organization {}: {}", orgId, e.getMessage(), e);
            return new RedirectView("/admin/" + orgId + "/calendar-entries?errorMessage=Reimport+failed:+" + e.getMessage());
        }
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
}