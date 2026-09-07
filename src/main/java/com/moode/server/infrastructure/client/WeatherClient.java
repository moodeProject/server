package com.moode.server.infrastructure.client;

import com.moode.server.infrastructure.dto.response.KmaWeatherResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "weather-client", url = "${weather.api.url}")
public interface WeatherClient {

    @GetMapping("/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst")
    KmaWeatherResponse getUltraSrtNcst(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("dataType") String dataType,
            @RequestParam("base_date") String baseDate,
            @RequestParam("base_time") String baseTime,
            @RequestParam("nx") int nx,
            @RequestParam("ny") int ny
    );
}
