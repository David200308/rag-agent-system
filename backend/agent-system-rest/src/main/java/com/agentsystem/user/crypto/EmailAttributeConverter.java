package com.agentsystem.user.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies {@link EmailEncryptor} transparently on {@code User.email} — Hibernate calls this
 * for both entity load/save and derived-query parameters (e.g. {@code findByEmail(raw)}),
 * so repository code never has to encrypt/decrypt manually.
 */
@Converter(autoApply = false)
@Component
@RequiredArgsConstructor
public class EmailAttributeConverter implements AttributeConverter<String, String> {

    private final EmailEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : encryptor.decrypt(dbData);
    }
}
