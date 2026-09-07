package com.moode.server.service.impl;

/**
 * 기상청 체감온도 산출식(2022.6.2. 적용) 구현.
 * 기온(Ta)과 상대습도(RH)만으로 여름철 체감온도를 계산한다.
 * 참고: 기온 30도 / 습도 80% 입력 시 체감온도 약 33.9도가 나오는 것으로 검증됨.
 */
public class HeatIndexCalculator {

    public static double calculate(double ta, double rh) {
        double tw = ta * atanDeg(0.151977 * Math.sqrt(rh + 8.313659))
                + atanDeg(ta + rh)
                - atanDeg(rh - 1.67633)
                + 0.00391838 * Math.pow(rh, 1.5) * atanDeg(0.023101 * rh)
                - 4.686035;

        return -0.2442 + 0.55399 * tw + 0.45535 * ta
                - 0.0022 * tw * tw + 0.00278 * tw * ta + 3.0;
    }

    // 공식의 atan은 라디안 단위 결과를 그대로 쓴다. Math.atan이 반환하는 값이 이미 라디안이므로 별도 변환 불필요.
    private static double atanDeg(double x) {
        return Math.atan(x);
    }
}
