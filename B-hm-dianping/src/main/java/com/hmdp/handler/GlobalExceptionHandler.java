package com.hmdp.handler;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /*
    * 本项目主要体现Redis的使用，异常处理代码统一采用RuntimeException处理
    * 捕获所有业务异常
    * */
    @ExceptionHandler
    public Result exceptionHandler(RuntimeException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.fail(ex.getMessage());
    }
}
