/**
 * Custom validation annotations and validators for ABC Bank Onboarding.
 *
 * <h2>Overview</h2>
 * <p>This package provides Jakarta Bean Validation (JSR-380) custom validators
 * specifically designed for Dutch banking regulations and data validation requirements.
 *
 * <h2>Available Validators</h2>
 *
 * <h3>@ValidDutchBSN</h3>
 * <p>Validates Dutch BSN (Burgerservicenummer / Social Security Number).</p>
 * <ul>
 *   <li>Exactly 9 digits</li>
 *   <li>No leading zeros</li>
 *   <li>11-proof checksum validation</li>
 * </ul>
 *
 * <pre>{@code
 * public class CustomerRequest {
 *     @ValidDutchBSN
 *     private String bsn;
 * }
 * }</pre>
 *
 * <h3>@ValidPhoneNumber</h3>
 * <p>Validates phone numbers using Google's libphonenumber library.</p>
 * <ul>
 *   <li>E.164 format (international format starting with +)</li>
 *   <li>Number possibility check</li>
 *   <li>Number validity check</li>
 *   <li>Support for Netherlands (+31) and international numbers</li>
 * </ul>
 *
 * <pre>{@code
 * public class CustomerRequest {
 *     @ValidPhoneNumber
 *     private String phoneNumber;  // e.g., "+31612345678"
 *
 *     @ValidPhoneNumber(allowNull = true)
 *     private String alternatePhone;  // Optional
 * }
 * }</pre>
 *
 * <h3>@ValidAddress</h3>
 * <p>Validates Dutch addresses.</p>
 * <ul>
 *   <li>Dutch postal code format: 1234 AB (4 digits + space + 2 letters)</li>
 *   <li>House number validation</li>
 *   <li>Country code validation (ISO 3166-1 alpha-2)</li>
 *   <li>All required fields must be present and non-empty</li>
 * </ul>
 *
 * <pre>{@code
 * public class CustomerRequest {
 *     @ValidAddress
 *     private Address address;
 *
 *     @ValidAddress(dutchOnly = false, allowNull = true)
 *     private Address billingAddress;  // International addresses allowed
 * }
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Complete Request DTO Example</h3>
 * <pre>{@code
 * public class OnboardingRequest {
 *     @NotBlank(message = "First name is required")
 *     @Size(min = 2, max = 50)
 *     private String firstName;
 *
 *     @NotBlank(message = "Last name is required")
 *     @Size(min = 2, max = 50)
 *     private String lastName;
 *
 *     @ValidDutchBSN
 *     private String bsn;
 *
 *     @NotNull(message = "Date of birth is required")
 *     @Past
 *     private LocalDate dateOfBirth;
 *
 *     @ValidPhoneNumber
 *     private String phoneNumber;
 *
 *     @Email
 *     private String email;
 *
 *     @ValidAddress
 *     private Address address;
 * }
 * }</pre>
 *
 * <h3>Controller Example</h3>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/onboarding")
 * public class OnboardingController {
 *
 *     @PostMapping
 *     public ResponseEntity<ApplicationResponse> createApplication(
 *             @Valid @RequestBody OnboardingRequest request) {
 *         // Validation is performed automatically
 *         // If validation fails, a MethodArgumentNotValidException is thrown
 *         return ResponseEntity.ok(applicationService.create(request));
 *     }
 * }
 * }</pre>
 *
 * <h3>Manual Validation Example</h3>
 * <pre>{@code
 * @Service
 * public class ValidationService {
 *     private final Validator validator;
 *
 *     public ValidationService(Validator validator) {
 *         this.validator = validator;
 *     }
 *
 *     public void validateCustomer(CustomerRequest request) {
 *         Set<ConstraintViolation<CustomerRequest>> violations =
 *             validator.validate(request);
 *
 *         if (!violations.isEmpty()) {
 *             String errors = violations.stream()
 *                 .map(ConstraintViolation::getMessage)
 *                 .collect(Collectors.joining(", "));
 *             throw new ValidationException(errors);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Error Response Format</h2>
 * <p>When validation fails, the GlobalExceptionHandler returns a structured error response:</p>
 * <pre>{@code
 * {
 *   "timestamp": "2025-11-02T17:45:00.123Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "path": "/api/v1/onboarding",
 *   "errors": {
 *     "bsn": "BSN failed 11-proof checksum validation",
 *     "phoneNumber": "Phone number must be in E.164 format (start with +)",
 *     "address.postalCode": "Dutch postal code must be in format '1234 AB'"
 *   }
 * }
 * }</pre>
 *
 * <h2>GDPR Compliance</h2>
 * <p>All validators are designed with GDPR compliance in mind:</p>
 * <ul>
 *   <li>No PII is logged during validation</li>
 *   <li>Error messages do not expose sensitive data</li>
 *   <li>Validators are stateless and thread-safe</li>
 *   <li>No validation data is cached or stored</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>All validators are stateless and thread-safe</li>
 *   <li>Phone number validation uses cached PhoneNumberUtil instance</li>
 *   <li>Regex patterns are pre-compiled for optimal performance</li>
 *   <li>BSN validation uses simple arithmetic (no external dependencies)</li>
 * </ul>
 *
 * <h2>Testing</h2>
 * <p>Validators can be tested using the Validator API:</p>
 * <pre>{@code
 * @SpringBootTest
 * class ValidatorTest {
 *     @Autowired
 *     private Validator validator;
 *
 *     @Test
 *     void testValidBSN() {
 *         TestRequest request = new TestRequest();
 *         request.setBsn("111222333");  // Valid BSN
 *
 *         Set<ConstraintViolation<TestRequest>> violations = validator.validate(request);
 *         assertTrue(violations.isEmpty());
 *     }
 *
 *     @Test
 *     void testInvalidBSN() {
 *         TestRequest request = new TestRequest();
 *         request.setBsn("123456789");  // Invalid BSN
 *
 *         Set<ConstraintViolation<TestRequest>> violations = validator.validate(request);
 *         assertFalse(violations.isEmpty());
 *     }
 * }
 * }</pre>
 *
 * @see jakarta.validation.Constraint
 * @see jakarta.validation.ConstraintValidator
 * @see <a href="https://beanvalidation.org/">Jakarta Bean Validation</a>
 * @author ABC Bank Engineering Team
 * @version 1.0.0
 * @since 1.0.0
 */
package com.abcbank.onboarding.adapter.in.web.validation;
