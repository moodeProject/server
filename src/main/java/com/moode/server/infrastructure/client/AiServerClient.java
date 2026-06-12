package com.moode.server.infrastructure.client;

import com.moode.server.infrastructure.dto.request.AiRequest;
import com.moode.server.infrastructure.dto.response.AiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ai-server", url = "${ai.server.url}")
public interface AiServerClient {

    @PostMapping("/predict")
    AiResponse predict(@RequestBody AiRequest request);
}
