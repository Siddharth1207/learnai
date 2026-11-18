package com.learnai.property;

import jakarta.validation.constraints.NotBlank;

public record PurchaseRequest(@NotBlank String buyerName, @NotBlank String buyerContact) {
}
