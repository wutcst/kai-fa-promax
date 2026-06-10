package com.guandan.vo;

import lombok.Data;

/**
 * 分页结果包装类
 */
@Data
public class PageResult<T> {

    private java.util.List<T> records;
    private long total;
    private long current;
    private long size;
    private long totalPages;

    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotalPages(page.getTotal() > 0 ? (page.getTotal() + page.getSize() - 1) / page.getSize() : 0);
        return result;
    }
}
