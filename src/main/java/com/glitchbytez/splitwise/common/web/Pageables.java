package com.glitchbytez.splitwise.common.web;

import java.util.Set;
import java.util.TreeSet;

import com.glitchbytez.splitwise.common.error.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Guards list endpoints against sorting by arbitrary (possibly non-existent or internal) properties. */
public final class Pageables {

    private Pageables() {
    }

    public static Pageable requireSortableBy(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: "
                        + String.join(", ", new TreeSet<>(allowedProperties)));
            }
        }
        return pageable;
    }
}
