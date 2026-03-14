# Chutes.io LLMClient for Koog

This package contains an implementation of LLMClient for [Chutes.io](https://chutes.ai), a decentralized AI inference platform powered by Bittensor.

## Files

- **ChutesClientSettings.kt** - Configuration settings for connecting to Chutes API
- **ChutesModels.kt** - Data models for Chutes API requests/responses
- **ChutesLLMClient.kt** - Main client implementation

## Usage

```kotlin
import info.skyblond.llm.summary.rag.chutes.ChutesLLMClient
import info.skyblond.llm.summary.rag.chutes.ChutesClientSettings
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

// Create client with your API key
val client = ChutesLLMClient(
    apiKey = "your-chutes-api-key"
)

// Or with custom settings
val clientWithSettings = ChutesLLMClient(
    apiKey = "your-chutes-api-key",
    settings = ChutesClientSettings(
        baseUrl = "https://llm.chutes.ai/v1",
        modelsPath = "/models"
    )
)

// Get available models
val models: List<LLModel> = client.models()

// Use with a model
val model = models.first()
val params = LLMParams(temperature = 0.7, maxTokens = 1000)

// Execute prompts using Koog's standard API
val response = client.complete(prompt, model, params)
```

## API Reference

### ChutesClientSettings

- `baseUrl`: Base URL for Chutes API (default: `"https://llm.chutes.ai/v1"`)
- `chatCompletionsPath`: Chat completions endpoint path (default: `"/chat/completions"`)
- `modelsPath`: Models list endpoint path (default: `"/models"`)
- `timeoutConfig`: Connection timeout configuration

### Supported Features

- ✅ Chat completions (streaming and non-streaming)
- ✅ Tool/function calling
- ✅ Temperature, maxTokens, topP, seed parameters
- ✅ Response format (JSON schema)
- ✅ Stop sequences
- ✅ Model listing
- ❌ Moderation (not supported by Chutes API)

## Getting API Key

1. Register on [Chutes platform](https://chutes.ai)
2. Create API keys using the CLI:
   ```bash
   chutes keys create --name my-key
   ```
3. Use the generated key in your client

## Pricing

Chutes charges based on GPU usage time, not tokens. See [pricing](https://api.chutes.ai/pricing) for current rates.

## Notes

- Chutes uses OpenAI-compatible API, so it reuses OpenAI provider in Koog
- All standard OpenAI parameters are supported where applicable
- For advanced parameters, use `additionalProperties` in LLMParams
