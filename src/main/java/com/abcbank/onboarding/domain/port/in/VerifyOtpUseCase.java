package com.abcbank.onboarding.domain.port.in;

import com.abcbank.onboarding.domain.model.OtpVerification;

import java.util.UUID;

/**
 * Use case for verifying OTP.
 */
public interface VerifyOtpUseCase {

    String execute(VerifyOtpCommand command);

    record VerifyOtpCommand(
            UUID applicationId,
            String otp,
            OtpVerification.OtpChannel channel
    ) {}
}
