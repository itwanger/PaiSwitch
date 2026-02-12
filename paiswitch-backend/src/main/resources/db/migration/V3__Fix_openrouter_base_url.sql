-- Normalize OpenRouter base URL for Anthropic-compatible /v1/messages path handling
UPDATE model_provider
SET base_url = 'https://openrouter.ai/api'
WHERE code = 'openrouter'
  AND base_url = 'https://openrouter.ai/api/v1';
