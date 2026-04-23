package com.contractlens.service.lightrag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contractlens.lightrag")
public class LightRagProperties {

    private boolean enabled = false;
    private String baseUrl;
    private String queryPath = "/query";
    private String queryMode = "hybrid";
    private boolean onlyNeedContext = true;
    private String inputsDir;
    private boolean clearInputsOnRebuild = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getQueryPath() {
        return queryPath;
    }

    public void setQueryPath(String queryPath) {
        this.queryPath = queryPath;
    }

    public String getQueryMode() {
        return queryMode;
    }

    public void setQueryMode(String queryMode) {
        this.queryMode = queryMode;
    }

    public boolean isOnlyNeedContext() {
        return onlyNeedContext;
    }

    public void setOnlyNeedContext(boolean onlyNeedContext) {
        this.onlyNeedContext = onlyNeedContext;
    }

    public String getInputsDir() {
        return inputsDir;
    }

    public void setInputsDir(String inputsDir) {
        this.inputsDir = inputsDir;
    }

    public boolean isClearInputsOnRebuild() {
        return clearInputsOnRebuild;
    }

    public void setClearInputsOnRebuild(boolean clearInputsOnRebuild) {
        this.clearInputsOnRebuild = clearInputsOnRebuild;
    }
}

