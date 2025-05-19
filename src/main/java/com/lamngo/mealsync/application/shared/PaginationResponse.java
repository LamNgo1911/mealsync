package com.lamngo.mealsync.application.shared;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaginationResponse<T> {
    private List<T> data;
    private int offset;
    private int limit;
    private long totalElements;
    private boolean hasNext;
}
