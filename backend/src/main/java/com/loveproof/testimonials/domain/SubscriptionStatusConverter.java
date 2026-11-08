package com.loveproof.testimonials.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link SubscriptionStatus} as the lowercase string Stripe uses.
 *
 * <p>The column and the wire format then agree, so there is no mapping table
 * to keep in step with anything.
 */
@Converter(autoApply = true)
public class SubscriptionStatusConverter implements AttributeConverter<SubscriptionStatus, String> {

    @Override
    public String convertToDatabaseColumn(SubscriptionStatus attribute) {
        return attribute == null ? SubscriptionStatus.NONE.wireValue() : attribute.wireValue();
    }

    @Override
    public SubscriptionStatus convertToEntityAttribute(String dbData) {
        return SubscriptionStatus.fromWireValue(dbData);
    }
}
