/**
 * Custom JavaScript for SSH Server Configuration web interface
 */

// Wait for the DOM to be fully loaded
document.addEventListener('DOMContentLoaded', function() {
    // Initialize Bootstrap tooltips
    var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    var tooltipList = tooltipTriggerList.map(function(tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
    
    // Initialize Bootstrap popovers
    var popoverTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="popover"]'));
    var popoverList = popoverTriggerList.map(function(popoverTriggerEl) {
        return new bootstrap.Popover(popoverTriggerEl);
    });
    
    // Form validation
    var forms = document.querySelectorAll('.needs-validation');
    
    // Loop over them and prevent submission
    Array.prototype.slice.call(forms).forEach(function(form) {
        form.addEventListener('submit', function(event) {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
            }
            
            form.classList.add('was-validated');
        }, false);
    });
    
    // Password toggle visibility
    var passwordToggles = document.querySelectorAll('.password-toggle');
    
    passwordToggles.forEach(function(toggle) {
        toggle.addEventListener('click', function() {
            var passwordField = document.querySelector(this.getAttribute('data-target'));
            
            if (passwordField.type === 'password') {
                passwordField.type = 'text';
                this.innerHTML = '<i class="bi bi-eye-slash"></i>';
            } else {
                passwordField.type = 'password';
                this.innerHTML = '<i class="bi bi-eye"></i>';
            }
        });
    });
    
    // Auto-dismiss alerts after 5 seconds
    var alerts = document.querySelectorAll('.alert:not(.alert-danger)');
    
    alerts.forEach(function(alert) {
        setTimeout(function() {
            var bsAlert = new bootstrap.Alert(alert);
            bsAlert.close();
        }, 5000);
    });
    
    // Confirm delete actions
    var deleteButtons = document.querySelectorAll('.btn-delete-confirm');
    
    deleteButtons.forEach(function(button) {
        button.addEventListener('click', function(event) {
            if (!confirm('Are you sure you want to delete this configuration? This action cannot be undone.')) {
                event.preventDefault();
            }
        });
    });
    
    // Add event listener to port input to ensure it's within valid range
    var portInput = document.getElementById('port');
    
    if (portInput) {
        portInput.addEventListener('input', function() {
            var value = parseInt(this.value, 10);
            
            if (value < 1) {
                this.value = 1;
            } else if (value > 65535) {
                this.value = 65535;
            }
        });
    }
});