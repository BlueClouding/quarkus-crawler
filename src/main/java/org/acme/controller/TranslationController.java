package org.acme.controller;


import org.acme.service.translation.OptimizedSubtitleTranslationService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/translate")
public class TranslationController {

    @Inject
    OptimizedSubtitleTranslationService translationService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String translate(TranslationRequest request) {
        // Use the optimized translation service which preserves formatting
        // For plain text we're still using the same method but benefiting from optimizations
        return translationService.translateSubtitle(request.getText(), request.getSourceLanguage(), request.getTargetLanguage());
    }
}

class TranslationRequest {
    private String text;
    private String sourceLanguage;
    private String targetLanguage;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }
}