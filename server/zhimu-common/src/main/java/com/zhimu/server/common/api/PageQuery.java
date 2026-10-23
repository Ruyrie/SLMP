package com.zhimu.server.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页与排序参数基类：page 从 1 开始，size 默认 20、最大 100；sort 为字段名，order 为 asc / desc。
 * 各模块的 XxxQuery 继承它。查询参数名为 snake_case（如 start_date），由框架的参数绑定转换为驼峰属性。
 */
@Getter
@Setter
public class PageQuery {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为 1")
    @Max(value = MAX_SIZE, message = "每页最多 100 条")
    private Integer size = DEFAULT_SIZE;

    private String sort;

    private String order;

    public int pageOrDefault() {
        return page == null || page < 1 ? 1 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public boolean isAsc() {
        return "asc".equalsIgnoreCase(order);
    }
}
