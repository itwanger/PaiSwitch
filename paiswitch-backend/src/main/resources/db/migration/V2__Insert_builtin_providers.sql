-- Insert built-in model providers
INSERT INTO model_provider (code, name, description, base_url, model_name, model_name_small, is_builtin, sort_order) VALUES
('claude', 'Claude (Official)', 'Anthropic Claude official API', 'https://api.anthropic.com', 'claude-sonnet-4-20250514', 'claude-3-5-haiku-latest', TRUE, 1),
('deepseek', 'DeepSeek V3', 'DeepSeek AI model with Anthropic compatible API', 'https://api.deepseek.com/anthropic', 'deepseek-chat', NULL, TRUE, 2),
('zhipu', 'Zhipu AI', 'Zhipu GLM model with Anthropic compatible API', 'https://open.bigmodel.cn/api/anthropic', 'glm-4.5', 'glm-4.5-air', TRUE, 3),
('openrouter', 'OpenRouter', 'OpenRouter multi-model gateway', 'https://openrouter.ai/api/v1', 'anthropic/claude-sonnet-4', 'anthropic/claude-3-haiku', TRUE, 4);
