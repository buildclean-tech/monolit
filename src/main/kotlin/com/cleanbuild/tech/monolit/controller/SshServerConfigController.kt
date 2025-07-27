package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.dto.SshServerConfigDto
import com.cleanbuild.tech.monolit.service.SshServerConfigService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * Controller for managing SSH server configurations.
 * This controller handles HTTP requests for listing, creating, updating, and deleting SSH server configurations.
 */
@Controller
@RequestMapping("/ssh-configs")
class SshServerConfigController @Autowired constructor(
    private val sshServerConfigService: SshServerConfigService
) {
    private val logger = LoggerFactory.getLogger(SshServerConfigController::class.java)
    
    /**
     * Display a list of all SSH server configurations.
     *
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping
    fun listConfigs(model: Model): String {
        logger.info("Listing all SSH server configurations")
        val configs = sshServerConfigService.findAll()
        model.addAttribute("configs", configs.map { SshServerConfigDto.fromEntity(it) })
        return "ssh-configs/list"
    }
    
    /**
     * Display the form for creating a new SSH server configuration.
     *
     * @param model the model to add attributes to
     * @return the name of the view to render
     */
    @GetMapping("/new")
    fun newConfigForm(model: Model): String {
        logger.info("Displaying form for creating a new SSH server configuration")
        model.addAttribute("config", SshServerConfigDto())
        model.addAttribute("isNew", true)
        return "ssh-configs/form"
    }
    
    /**
     * Handle the submission of the form for creating a new SSH server configuration.
     *
     * @param config the SSH server configuration data from the form
     * @param bindingResult the result of the validation
     * @param redirectAttributes attributes to add to the redirect
     * @return the name of the view to render or redirect to
     */
    @PostMapping
    fun createConfig(
        @Valid @ModelAttribute("config") config: SshServerConfigDto,
        bindingResult: BindingResult,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Creating a new SSH server configuration: {}", config)
        
        if (bindingResult.hasErrors()) {
            logger.warn("Validation errors: {}", bindingResult.allErrors)
            return "ssh-configs/form"
        }
        
        try {
            val savedConfig = sshServerConfigService.save(config.toEntity())
            redirectAttributes.addFlashAttribute("message", "SSH server configuration created successfully")
            redirectAttributes.addFlashAttribute("messageType", "success")
            return "redirect:/ssh-configs"
        } catch (e: Exception) {
            logger.error("Error creating SSH server configuration", e)
            redirectAttributes.addFlashAttribute("message", "Error creating SSH server configuration: ${e.message}")
            redirectAttributes.addFlashAttribute("messageType", "danger")
            return "redirect:/ssh-configs/new"
        }
    }
    
    /**
     * Display the form for editing an existing SSH server configuration.
     *
     * @param id the ID of the configuration to edit
     * @param model the model to add attributes to
     * @param redirectAttributes attributes to add to the redirect
     * @return the name of the view to render or redirect to
     */
    @GetMapping("/{id}/edit")
    fun editConfigForm(
        @PathVariable id: Long,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Displaying form for editing SSH server configuration with ID: {}", id)
        
        val configOpt = sshServerConfigService.findById(id)
        if (!configOpt.isPresent) {
            logger.warn("SSH server configuration with ID {} not found", id)
            redirectAttributes.addFlashAttribute("message", "SSH server configuration not found")
            redirectAttributes.addFlashAttribute("messageType", "danger")
            return "redirect:/ssh-configs"
        }
        
        model.addAttribute("config", SshServerConfigDto.fromEntity(configOpt.get()))
        model.addAttribute("isNew", false)
        return "ssh-configs/form"
    }
    
    /**
     * Handle the submission of the form for updating an existing SSH server configuration.
     *
     * @param id the ID of the configuration to update
     * @param config the SSH server configuration data from the form
     * @param bindingResult the result of the validation
     * @param redirectAttributes attributes to add to the redirect
     * @return the name of the view to render or redirect to
     */
    @PostMapping("/{id}")
    fun updateConfig(
        @PathVariable id: Long,
        @Valid @ModelAttribute("config") config: SshServerConfigDto,
        bindingResult: BindingResult,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Updating SSH server configuration with ID {}: {}", id, config)
        
        if (bindingResult.hasErrors()) {
            logger.warn("Validation errors: {}", bindingResult.allErrors)
            return "ssh-configs/form"
        }
        
        val configOpt = sshServerConfigService.findById(id)
        if (!configOpt.isPresent) {
            logger.warn("SSH server configuration with ID {} not found", id)
            redirectAttributes.addFlashAttribute("message", "SSH server configuration not found")
            redirectAttributes.addFlashAttribute("messageType", "danger")
            return "redirect:/ssh-configs"
        }
        
        try {
            val existingConfig = configOpt.get()
            existingConfig.serverHost = config.serverHost
            existingConfig.port = config.port
            existingConfig.password = config.password
            
            val savedConfig = sshServerConfigService.save(existingConfig)
            redirectAttributes.addFlashAttribute("message", "SSH server configuration updated successfully")
            redirectAttributes.addFlashAttribute("messageType", "success")
            return "redirect:/ssh-configs"
        } catch (e: Exception) {
            logger.error("Error updating SSH server configuration", e)
            redirectAttributes.addFlashAttribute("message", "Error updating SSH server configuration: ${e.message}")
            redirectAttributes.addFlashAttribute("messageType", "danger")
            return "redirect:/ssh-configs/${id}/edit"
        }
    }
    
    /**
     * Display the confirmation page for deleting an SSH server configuration.
     *
     * @param id the ID of the configuration to delete
     * @param model the model to add attributes to
     * @param redirectAttributes attributes to add to the redirect
     * @return the name of the view to render or redirect to
     */
    @GetMapping("/{id}/delete")
    fun deleteConfigConfirmation(
        @PathVariable id: Long,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Displaying confirmation page for deleting SSH server configuration with ID: {}", id)
        
        val configOpt = sshServerConfigService.findById(id)
        if (!configOpt.isPresent) {
            logger.warn("SSH server configuration with ID {} not found", id)
            redirectAttributes.addFlashAttribute("message", "SSH server configuration not found")
            redirectAttributes.addFlashAttribute("messageType", "danger")
            return "redirect:/ssh-configs"
        }
        
        model.addAttribute("config", SshServerConfigDto.fromEntity(configOpt.get()))
        return "ssh-configs/delete"
    }
    
    /**
     * Handle the deletion of an SSH server configuration.
     *
     * @param id the ID of the configuration to delete
     * @param redirectAttributes attributes to add to the redirect
     * @return the name of the view to redirect to
     */
    @PostMapping("/{id}/delete")
    fun deleteConfig(
        @PathVariable id: Long,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Deleting SSH server configuration with ID: {}", id)
        
        try {
            sshServerConfigService.deleteById(id)
            redirectAttributes.addFlashAttribute("message", "SSH server configuration deleted successfully")
            redirectAttributes.addFlashAttribute("messageType", "success")
        } catch (e: Exception) {
            logger.error("Error deleting SSH server configuration", e)
            redirectAttributes.addFlashAttribute("message", "Error deleting SSH server configuration: ${e.message}")
            redirectAttributes.addFlashAttribute("messageType", "danger")
        }
        
        return "redirect:/ssh-configs"
    }
}