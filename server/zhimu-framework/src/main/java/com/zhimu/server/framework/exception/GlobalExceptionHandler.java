package com.zhimu.server.framework.exception;

import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全局异常处理：响应体为 ApiResponse，HTTP 状态码等于错误码前三位。拦截器中抛出的 BizException 同样经过这里。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String MSG_PARAM_INVALID = "参数校验失败";
    static final String MSG_BAD_FORMAT = "请求格式不正确";
    static final String MSG_FILE_TOO_LARGE = "文件过大";
    static final String MSG_API_NOT_FOUND = "接口不存在";
    static final String MSG_DUPLICATE = "数据已存在，请刷新后重试";
    static final String MSG_FIELD_FORMAT = "格式不正确";
    static final String MSG_FIELD_REQUIRED = "不能为空";

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e) {
        if (e.getHttpStatus() >= 500) {
            log.warn("业务异常 {} {}", e.getCode(), e.getMessage());
        } else {
            log.debug("业务异常 {} {}", e.getCode(), e.getMessage());
        }
        return respond(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBind(BindException e) {
        List<FieldErrorVO> errors = new ArrayList<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorVO(FieldPaths.toSnake(fe.getField()),
                    fe.isBindingFailure() ? MSG_FIELD_FORMAT : fe.getDefaultMessage()));
        }
        String globalMessage = e.getBindingResult().getGlobalErrors().stream()
                .map(ObjectError::getDefaultMessage).findFirst().orElse(null);
        return paramInvalid(errors, globalMessage);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodValidation(HandlerMethodValidationException e) {
        List<FieldErrorVO> errors = new ArrayList<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            String prefix = result.getContainerIndex() != null ? "[" + result.getContainerIndex() + "]"
                    : result.getContainerKey() != null ? "[" + result.getContainerKey() + "]" : "";
            if (result instanceof ParameterErrors pe) {
                for (FieldError fe : pe.getFieldErrors()) {
                    String field = prefix.isEmpty() ? fe.getField() : prefix + "." + fe.getField();
                    errors.add(new FieldErrorVO(FieldPaths.toSnake(field),
                            fe.isBindingFailure() ? MSG_FIELD_FORMAT : fe.getDefaultMessage()));
                }
            } else {
                String name = FieldPaths.toSnake(requestName(result.getMethodParameter())) + prefix;
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    errors.add(new FieldErrorVO(name, error.getDefaultMessage()));
                }
            }
        }
        String crossMessage = e.getCrossParameterValidationResults().stream()
                .map(MessageSourceResolvable::getDefaultMessage).findFirst().orElse(null);
        return paramInvalid(errors, crossMessage);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorVO> errors = new ArrayList<>();
        if (e.getConstraintViolations() != null) {
            for (ConstraintViolation<?> v : e.getConstraintViolations()) {
                errors.add(new FieldErrorVO(FieldPaths.toSnake(violationPath(v.getPropertyPath())), v.getMessage()));
            }
        }
        return paramInvalid(errors, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return paramInvalid(List.of(new FieldErrorVO(FieldPaths.toSnake(e.getParameterName()), MSG_FIELD_REQUIRED)), null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPart(MissingServletRequestPartException e) {
        return paramInvalid(List.of(new FieldErrorVO(FieldPaths.toSnake(e.getRequestPartName()), MSG_FIELD_REQUIRED)), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return paramInvalid(List.of(new FieldErrorVO(FieldPaths.toSnake(e.getName()), MSG_FIELD_FORMAT)), null);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Object>> handleRequestBinding(ServletRequestBindingException e) {
        log.debug("请求绑定失败 {}", e.getMessage());
        return respond(ErrorCode.PARAM_INVALID.getCode(), MSG_BAD_FORMAT, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(Exception e) {
        log.debug("请求体无法解析 {}", e.getMessage());
        return respond(ErrorCode.PARAM_INVALID.getCode(), MSG_BAD_FORMAT, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadSize(MaxUploadSizeExceededException e) {
        return respond(ErrorCode.FILE_INVALID.getCode(), MSG_FILE_TOO_LARGE, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipart(MultipartException e) {
        log.debug("文件上传请求无法解析 {}", e.getMessage());
        return respond(ErrorCode.PARAM_INVALID.getCode(), MSG_BAD_FORMAT, null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiResponse<Object>> handleNotFound(Exception e) {
        return respond(ErrorCode.NOT_FOUND.getCode(), MSG_API_NOT_FOUND, null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突 {}", e.getMostSpecificCause().getMessage());
        return respond(ErrorCode.STATE_CHANGED.getCode(), MSG_DUPLICATE, null);
    }

    /** 客户端已断开（例如 SSE 中途关闭），无法再写响应 */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException e) {
        log.debug("客户端已断开 {}", e.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleAsyncTimeout(AsyncRequestTimeoutException e,
                                                                  HttpServletRequest request) {
        log.warn("异步请求超时 {} {}", request.getMethod(), request.getRequestURI());
        return respond(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ErrorCode.SERVICE_UNAVAILABLE.getDefaultMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e, HttpServletRequest request) {
        log.error("服务器内部错误 trace_id={} {} {}", TraceContext.get(), request.getMethod(), request.getRequestURI(), e);
        return respond(ErrorCode.SERVER_ERROR.getCode(), ErrorCode.SERVER_ERROR.getDefaultMessage(), null);
    }

    private static ResponseEntity<ApiResponse<Object>> paramInvalid(List<FieldErrorVO> errors, String fallbackMessage) {
        String message = errors.stream().map(FieldErrorVO::getMessage).filter(m -> m != null && !m.isBlank())
                .findFirst().orElse(fallbackMessage != null ? fallbackMessage : MSG_PARAM_INVALID);
        return respond(ErrorCode.PARAM_INVALID.getCode(), message, Map.of("field_errors", errors));
    }

    private static ResponseEntity<ApiResponse<Object>> respond(int code, String message, Object data) {
        return ResponseEntity.status(ErrorCode.httpStatusOf(code)).body(ApiResponse.fail(code, message, data));
    }

    /** 请求中的参数名：优先取 @RequestParam / @PathVariable / @RequestHeader 指定的名字 */
    private static String requestName(MethodParameter parameter) {
        RequestParam rp = parameter.getParameterAnnotation(RequestParam.class);
        if (rp != null && !rp.name().isEmpty()) {
            return rp.name();
        }
        PathVariable pv = parameter.getParameterAnnotation(PathVariable.class);
        if (pv != null && !pv.name().isEmpty()) {
            return pv.name();
        }
        RequestHeader rh = parameter.getParameterAnnotation(RequestHeader.class);
        if (rh != null && !rh.name().isEmpty()) {
            return rh.name();
        }
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }

    /** 去掉方法校验路径开头的“方法名.参数名”，只剩参数为简单值时用参数名 */
    private static String violationPath(Path path) {
        String parameterName = null;
        StringBuilder rest = new StringBuilder();
        for (Path.Node node : path) {
            switch (node.getKind()) {
                case METHOD, CONSTRUCTOR, RETURN_VALUE, CROSS_PARAMETER -> {
                }
                case PARAMETER -> parameterName = node.getName();
                default -> {
                    if (node.isInIterable()) {
                        rest.append('[').append(node.getIndex() != null ? node.getIndex() : node.getKey()).append(']');
                    }
                    if (node.getKind() == ElementKind.PROPERTY && node.getName() != null) {
                        if (!rest.isEmpty()) {
                            rest.append('.');
                        }
                        rest.append(node.getName());
                    }
                }
            }
        }
        if (rest.isEmpty()) {
            return parameterName != null ? parameterName : path.toString();
        }
        return rest.charAt(0) == '[' && parameterName != null ? parameterName + rest : rest.toString();
    }
}
