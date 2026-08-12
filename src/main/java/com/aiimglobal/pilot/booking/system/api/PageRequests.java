package com.aiimglobal.pilot.booking.system.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.aiimglobal.pilot.booking.system.exception.InvalidRequestParameterException;

public final class PageRequests {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable newestFirst(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestParameterException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestParameterException(
                    "Size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        Sort ordering = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(page, size, ordering);
    }
}
