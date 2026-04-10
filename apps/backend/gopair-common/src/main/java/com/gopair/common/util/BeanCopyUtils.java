package com.gopair.common.util;

import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对象属性复制工具类
 *
 * 用于统一处理项目中 PO, DTO, VO 之间的属性转换。
 * 基于 Spring 的 BeanUtils 实现。
 *
 *
 * @author gopair
 */
public class BeanCopyUtils {

    /**
     * 私有化构造函数，防止被实例化
     */
    private BeanCopyUtils() {
    }

    /**
     * 将单个对象转换为目标类型的对象
     *
     * @param source      源对象
     * @param targetClass 目标对象的 Class 类型
     * @param <T>         目标对象的泛型
     * @return 转换后的目标对象
     */
    public static <T> T copyBean(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("创建或复制Bean时出错", e);
        }
    }

    /**
     * 将对象列表转换为目标类型的对象列表
     *
     * @param sourceList  源对象列表
     * @param targetClass 目标对象的 Class 类型
     * @param <T>         目标对象的泛型
     * @return 转换后的目标对象列表
     */
    public static <T> List<T> copyBeanList(List<?> sourceList, Class<T> targetClass) {
        if (sourceList == null) {
            return null;
        }
        return sourceList.stream()
                .map(source -> copyBean(source, targetClass))
                .collect(Collectors.toList());
    }
}