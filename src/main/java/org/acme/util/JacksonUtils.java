package org.acme.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class JacksonUtils {

    private static final Logger logger = LoggerFactory.getLogger(JacksonUtils.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 常用配置 (可以根据项目需求调整)
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // 不序列化 null 值字段
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 日期序列化为 ISO 8601 格式
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")); // 设置默认日期格式
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); // 设置默认时区 (根据你的需求)
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // 反序列化时忽略未知属性
        // 可以添加更多自定义配置，例如注册自定义模块等
    }

    // 禁止实例化工具类
    private JacksonUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 获取全局唯一的 ObjectMapper 实例。
     *
     * @return ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串，如果发生错误则返回 null
     */
    public static String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing object to JSON string: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的对象。
     *
     * @param jsonString JSON 字符串
     * @param clazz      目标对象的 Class 类型
     * @param <T>        目标对象的类型
     * @return 反序列化后的对象，如果发生错误则返回 null
     */
    public static <T> T parseObject(String jsonString, Class<T> clazz) {
        if (jsonString == null || jsonString.isEmpty() || clazz == null) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, clazz);
        } catch (IOException e) {
            logger.error("Error parsing JSON string to object ({}): {}", clazz.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为 List 集合。
     *
     * @param jsonString JSON 字符串
     * @param elementType List 中元素的 Class 类型
     * @param <T>         List 中元素的类型
     * @return 反序列化后的 List 集合，如果发生错误则返回 null
     */
    public static <T> List<T> parseList(String jsonString, Class<T> elementType) {
        if (jsonString == null || jsonString.isEmpty() || elementType == null) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (IOException e) {
            logger.error("Error parsing JSON string to List<{}>: {}", elementType.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为 Map 集合。
     *
     * @param jsonString JSON 字符串
     * @param keyType    Map 中 Key 的 Class 类型
     * @param valueType  Map 中 Value 的 Class 类型
     * @param <K>        Map 中 Key 的类型
     * @param <V>        Map 中 Value 的类型
     * @return 反序列化后的 Map 集合，如果发生错误则返回 null
     */
    public static <K, V> Map<K, V> parseMap(String jsonString, Class<K> keyType, Class<V> valueType) {
        if (jsonString == null || jsonString.isEmpty() || keyType == null || valueType == null) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(Map.class, keyType, valueType));
        } catch (IOException e) {
            logger.error("Error parsing JSON string to Map<{}, {}>: {}", keyType.getSimpleName(), valueType.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 使用 TypeReference 反序列化复杂类型，例如带有泛型的 List 或 Map。
     *
     * @param jsonString    JSON 字符串
     * @param typeReference TypeReference 的实现，用于描述目标类型
     * @param <T>           目标类型
     * @return 反序列化后的对象，如果发生错误则返回 null
     */
    public static <T> T parseObject(String jsonString, TypeReference<T> typeReference) {
        if (jsonString == null || jsonString.isEmpty() || typeReference == null) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, typeReference);
        } catch (IOException e) {
            logger.error("Error parsing JSON string to object with TypeReference: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串解析为 JsonNode 对象，用于更灵活的 JSON 数据访问。
     *
     * @param jsonString JSON 字符串
     * @return JsonNode 对象，如果发生错误则返回 null
     */
    public static JsonNode parseJsonNode(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonString);
        } catch (IOException e) {
            logger.error("Error parsing JSON string to JsonNode: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 InputStream 中反序列化对象。
     *
     * @param inputStream 输入流
     * @param clazz       目标对象的 Class 类型
     * @param <T>         目标对象的类型
     * @return 反序列化后的对象，如果发生错误则返回 null
     */
    public static <T> T parseObject(InputStream inputStream, Class<T> clazz) {
        if (inputStream == null || clazz == null) {
            return null;
        }
        try {
            return objectMapper.readValue(inputStream, clazz);
        } catch (IOException e) {
            logger.error("Error parsing InputStream to object ({}): {}", clazz.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    // 可以添加更多便捷方法，例如将对象序列化为字节数组等

}
