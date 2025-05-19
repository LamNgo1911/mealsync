package com.lamngo.mealsync.application.shared;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
public class OffsetPage implements Pageable {

    @Min(1)
    private final int limit;

    @Min(0)
    private final int offset;

    private final Sort sort;

    public OffsetPage(int limit, int offset, Sort sort) {
        this.limit = limit;
        this.offset = offset;
        this.sort = sort != null ? sort : Sort.unsorted();
    }

    public OffsetPage(int limit, int offset) {
        this(limit, offset, Sort.unsorted());
    }

    @Override
    public int getPageNumber() {
        return offset / limit;
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPage(limit, offset + limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        int prevOffset = Math.max(offset - limit, 0);
        return new OffsetPage(limit, prevOffset, sort);
    }

    @Override
    public Pageable first() {
        return new OffsetPage(limit, 0, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPage(limit, pageNumber * limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
