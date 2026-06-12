package com.interview.common.result;

import lombok.Data;
import java.util.List;

/**
 * 通用分页结果封装
 */
@Data
public class PageResult<T> {
    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
    private long totalPages;

    public static <T> PageResult<T> of(List<T> list, long total, int page, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotalPages((int) Math.ceil((double) total / pageSize));
        return result;
    }
}
