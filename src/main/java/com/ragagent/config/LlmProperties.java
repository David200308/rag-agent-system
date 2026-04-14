package com.ragagent.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed binding for the {@code llm.*} block in application.yml.
 *
 * Switch provider at runtime via the LLM_PROVIDER env var:
 *   LLM_PROVIDER=openai      → uses llm.openai.*
 *   LLM_PROVIDER=anthropic   → uses llm.anthropic.*
 *   LLM_PROVIDER=openrouter  → uses llm.openrouter.*
 */
@Validated
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    @NotBlank
    private String provider = "openai";

    // Optional: override which provider handles embeddings independently of the chat LLM.
    // Defaults to null (inherits from provider). Set EMBEDDING_PROVIDER=local to use Ollama.
    private String embeddingProvider;

    private OpenAiProps openai = new OpenAiProps();
    private AnthropicProps anthropic = new AnthropicProps();
    private OpenRouterProps openrouter = new OpenRouterProps();
    private LocalProps local = new LocalProps();

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String embeddingProvider) { this.embeddingProvider = embeddingProvider; }

    public OpenAiProps getOpenai() { return openai; }
    public void setOpenai(OpenAiProps openai) { this.openai = openai; }

    public AnthropicProps getAnthropic() { return anthropic; }
    public void setAnthropic(AnthropicProps anthropic) { this.anthropic = anthropic; }

    public OpenRouterProps getOpenrouter() { return openrouter; }
    public void setOpenrouter(OpenRouterProps openrouter) { this.openrouter = openrouter; }

    public LocalProps getLocal() { return local; }
    public void setLocal(LocalProps local) { this.local = local; }

    // ── Nested config classes ─────────────────────────────────────────────

    public static class OpenAiProps {
        private String apiKey  = "";
        private String baseUrl = "https://api.openai.com";
        private String model   = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";
        private double temperature = 0.1;

        public String getApiKey()      { return apiKey; }
        public void   setApiKey(String v)   { this.apiKey = v; }
        public String getBaseUrl()     { return baseUrl; }
        public void   setBaseUrl(String v)  { this.baseUrl = v; }
        public String getModel()       { return model; }
        public void   setModel(String v)    { this.model = v; }
        public String getEmbeddingModel()   { return embeddingModel; }
        public void   setEmbeddingModel(String v) { this.embeddingModel = v; }
        public double getTemperature() { return temperature; }
        public void   setTemperature(double v) { this.temperature = v; }
    }

    public static class AnthropicProps {
        private String apiKey      = "";
        private String baseUrl     = "https://api.anthropic.com";
        private String model       = "claude-opus-4-6";
        private int    maxTokens   = 8096;
        private double temperature = 0.1;

        public String getApiKey()      { return apiKey; }
        public void   setApiKey(String v)   { this.apiKey = v; }
        public String getBaseUrl()     { return baseUrl; }
        public void   setBaseUrl(String v)  { this.baseUrl = v; }
        public String getModel()       { return model; }
        public void   setModel(String v)    { this.model = v; }
        public int    getMaxTokens()   { return maxTokens; }
        public void   setMaxTokens(int v)   { this.maxTokens = v; }
        public double getTemperature() { return temperature; }
        public void   setTemperature(double v) { this.temperature = v; }
    }

    public static class OpenRouterProps {
        private String apiKey          = "";
        private String baseUrl         = "https://openrouter.ai/api/v1";
        private String model           = "openai/gpt-4o-mini";
        private String embeddingModel  = "openai/text-embedding-3-small";
        private double temperature     = 0.1;
        private String siteUrl         = "";
        private String siteName        = "rag-agent-system";

        public String getApiKey()      { return apiKey; }
        public void   setApiKey(String v)   { this.apiKey = v; }
        public String getBaseUrl()     { return baseUrl; }
        public void   setBaseUrl(String v)  { this.baseUrl = v; }
        public String getModel()       { return model; }
        public void   setModel(String v)    { this.model = v; }
        public String getEmbeddingModel()   { return embeddingModel; }
        public void   setEmbeddingModel(String v) { this.embeddingModel = v; }
        public double getTemperature() { return temperature; }
        public void   setTemperature(double v) { this.temperature = v; }
        public String getSiteUrl()     { return siteUrl; }
        public void   setSiteUrl(String v)  { this.siteUrl = v; }
        public String getSiteName()    { return siteName; }
        public void   setSiteName(String v) { this.siteName = v; }
    }

    /**
     * Local LLM server (Ollama, LM Studio, llama.cpp, etc.).
     * All of these expose an OpenAI-compatible /v1 endpoint — no API key needed.
     *
     * Spring AI appends /v1/chat/completions to this base URL — do NOT include /v1.
     * Ollama default:   http://localhost:11434
     * LM Studio default: http://localhost:1234
     */
    public static class LocalProps {
        private String baseUrl        = "http://localhost:11434";
        private String model          = "llama3";
        private String embeddingModel = "nomic-embed-text";
        private double temperature    = 0.1;

        public String getBaseUrl()          { return baseUrl; }
        public void   setBaseUrl(String v)       { this.baseUrl = v; }
        public String getModel()            { return model; }
        public void   setModel(String v)         { this.model = v; }
        public String getEmbeddingModel()   { return embeddingModel; }
        public void   setEmbeddingModel(String v){ this.embeddingModel = v; }
        public double getTemperature()      { return temperature; }
        public void   setTemperature(double v)   { this.temperature = v; }
    }
}
