package com.paicoding.paiswitch.controller;

import com.paicoding.paiswitch.common.response.ApiResponse;
import com.paicoding.paiswitch.common.security.JwtTokenProvider;
import com.paicoding.paiswitch.domain.dto.SwitchDto;
import com.paicoding.paiswitch.service.ai.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI Interaction", description = "AI natural language interaction APIs")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final AiChatService aiChatService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Natural language model switching",
               description = "Use natural language to interact with AI and switch models. Examples: '切换到 DeepSeek', '帮我换成智谱 AI'")
    @PostMapping("/switch-by-nl")
    public ApiResponse<SwitchDto.NaturalLanguageResponse> switchByNaturalLanguage(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SwitchDto.NaturalLanguageRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(aiChatService.processNaturalLanguage(userId, request));
    }

    @Operation(summary = "Chat with AI assistant")
    @PostMapping("/chat")
    public ApiResponse<SwitchDto.NaturalLanguageResponse> chat(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SwitchDto.NaturalLanguageRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(aiChatService.processNaturalLanguage(userId, request));
    }

    private Long extractUserId(String authorization) {
        String token = authorization.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
