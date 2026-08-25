package com.moode.server.exception;

import com.moode.server.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.error("입력값 검증 실패: {}", message);
        return ApiResponse.fail(message);
    }

    // 본문 JSON 자체가 깨진 경우. 재시도해도 똑같이 실패하는 영구 오류라 400 으로 응답해
    // AI 서버가 재시도 대신 dead-letter 로 보내게 한다. (이 핸들러가 없으면 500 -> 무의미한 5회 재시도)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.error("본문을 파싱할 수 없음: {}", e.getMessage());
        return ApiResponse.fail("요청 본문을 파싱할 수 없습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.error("잘못된 요청: {}", e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException e) {
        log.error("인증 실패: {}", e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(ConflictException e) {
        log.warn("충돌: {}", e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleUnprocessable(UnprocessableEntityException e) {
        log.warn("처리 불가: {}", e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFoundException(NotFoundException e) {
        log.error("리소스 없음: {}", e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    // 여기로 떨어지는 모든 예외는 500 이 된다. AI 서버 연동 규약상 5xx 는 "재시도하라"는 뜻이므로
    // 일시적 장애(DB 커넥션, 락 경합 등)가 400 으로 내려가 이벤트가 유실되지 않도록
    // 새 예외를 400 계열로 매핑할 때는 그것이 정말 영구 오류인지 확인할 것.
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("서버 오류: {}", e.getMessage(), e);
        return ApiResponse.fail("서버 내부 오류가 발생했습니다.");
    }
}
