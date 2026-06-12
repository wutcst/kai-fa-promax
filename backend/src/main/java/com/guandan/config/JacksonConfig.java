package com.guandan.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置
 *
 * <p>统一管理日期格式、null 值处理、特殊字符转义等序列化行为，
 * 减少 payload 体积，提高 API 响应效率。
 *
 * <p><b>优化点：</b>
 * <ul>
 *   <li>日期格式统一为 yyyy-MM-dd HH:mm:ss，客户端无需额外解析</li>
 *   <li>null 字段不输出，减少冗余 JSON 键</li>
 *   <li>特殊字符转义（防 XSS 和 JSON 注入）</li>
 *   <li>Long 类型转 String（防止前端 JS 精度丢失）</li>
 * </ul>
 *
 * <p><b>回归验证点：</b>
 * <ul>
 *   <li>[RV-JACKSON-001] LocalDateTime 序列化为 "yyyy-MM-dd HH:mm:ss" 格式</li>
 *   <li>[RV-JACKSON-002] null 字段不再出现在 JSON 响应中</li>
 *   <li>[RV-JACKSON-003] 含 HTML 标签的字符串被正确转义</li>
 *   <li>[RV-JACKSON-004] Long 类型（超过 2^53）以字符串形式返回</li>
 * </ul>
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /** 日期时间格式：年-月-日 时:分:秒 */
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 日期格式：年-月-日 */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 时间格式：时:分:秒 */
    private static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 全局 ObjectMapper Bean
     *
     * 配置要点：
     * 1. 注册 JavaTimeModule 以支持 Java 8 日期时间类型
     * 2. 设置 LocalDateTime/LocalDate/LocalTime 序列化格式
     * 3. 禁止将日期写为时间戳数组（WRITE_DATES_AS_TIMESTAMPS=false）
     * 4. 禁止 null 字段输出（WRITE_NULL_MAP_VALUES=false + setSerializationInclusion）
     * 5. 启用特殊字符转义（防止 HTML/JS 注入）
     * 6. Long -> String 转换（解决前端 JS Number 精度丢失）
     *
     * 异常场景：
     * - 日期值为 null → 不输出该字段（而非输出 "null" 字符串）
     * - 字符串含 <script> 标签 → 自动转义为 <script>
     *
     * @return 配置完成的 ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();

        // Java 8 日期时间模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 注册各类型序列化器
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_PATTERN)));
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));

        // Long -> String 序列化（防止前端精度丢失）
        SimpleModule longToStringModule = new SimpleModule();
        longToStringModule.addSerializer(Long.class, new ToStringSerializer());
        longToStringModule.addSerializer(Long.TYPE, new ToStringSerializer());

        // 注册所有模块
        builder.modules(javaTimeModule, longToStringModule);

        // 禁用日期时间戳输出
        builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // null 值处理：不输出 null 字段
        builder.serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        // 构建 ObjectMapper
        ObjectMapper objectMapper = builder.build();

        // 配置特殊字符转义（防 XSS 注入）
        objectMapper.getFactory().setCharacterEscapes(new com.fasterxml.jackson.core.io.CharacterEscapes() {
            private final int[] asciiEscapes = com.fasterxml.jackson.core.io.CharacterEscapes.standardAsciiEscapesForJSON();

            @Override
            public int[] getEscapeCodesForAscii() {
                int[] escapes = new int[128];
                System.arraycopy(asciiEscapes, 0, escapes, 0, 128);
                // 额外转义 HTML 特殊字符
                escapes['<'] = JsonGenerator.EscapeCode.HANDLE_NAME;
                escapes['>'] = JsonGenerator.EscapeCode.HANDLE_NAME;
                escapes['&'] = JsonGenerator.EscapeCode.HANDLE_NAME;
                escapes['\''] = JsonGenerator.EscapeCode.HANDLE_NAME;
                return escapes;
            }

            @Override
            public java.io.Serializable getEscapeSequence(int ch) {
                switch (ch) {
                    case '<':  return "\\u003C";
                    case '>':  return "\\u003E";
                    case '&':  return "\\u0026";
                    case '\'': return "\\u0027";
                    default:   return null;
                }
            }
        });

        log.info("Jackson 全局序列化配置加载完成");
        log.info("  日期格式: {} / {} / {}", DATETIME_PATTERN, DATE_PATTERN, TIME_PATTERN);
        log.info("  null 字段: 不输出");
        log.info("  特殊字符: < > & ' 已转义");
        log.info("  Long 类型: 转 String 输出");

        return objectMapper;
    }

    /**
     * Spring MVC HttpMessageConverter 覆盖
     *
     * 确保所有 REST 接口都使用统一配置的 ObjectMapper，
     * 而不是默认的 Jackson 序列化行为。
     *
     * @return 配置完成的 MappingJackson2HttpMessageConverter
     */
    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    /**
     * 初始化日志输出，确认配置生效
     */
    @PostConstruct
    public void init() {
        log.info("Jackson 序列化配置已注入 Spring 容器");
        log.info("  - LocalDateTime 格式: {}", DATETIME_PATTERN);
        log.info("  - null 值处理: NON_NULL");
        log.info("  - HTML 字符转义: 已启用");
    }
}