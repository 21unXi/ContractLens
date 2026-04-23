package com.contractlens.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contractlens.rag")
public class RagProperties {

    private RagMode mode = RagMode.LEGACY;
    private boolean fallbackToLegacy = true;

    public RagMode getMode() {
        return mode;
    }

    public void setMode(RagMode mode) {
        this.mode = mode;
    }

    public boolean isFallbackToLegacy() {
        return fallbackToLegacy;
    }

    public void setFallbackToLegacy(boolean fallbackToLegacy) {
        this.fallbackToLegacy = fallbackToLegacy;
    }
}

