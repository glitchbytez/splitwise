package com.glitchbytez.splitwise.common.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.glitchbytez.splitwise.common.error.BadRequestException;
import org.springframework.data.domain.PageRequest;
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

    /**
     * Validates the requested sort against the API's public property names and translates them to entity
     * properties, e.g. {@code date -> expenseDate}. {@code tieBreakers} are appended so equal values keep a
     * stable order across pages.
     */
    public static Pageable translateSort(Pageable pageable, Map<String, String> apiToEntityProperty,
                                         Sort.Order... tieBreakers) {
        requireSortableBy(pageable, apiToEntityProperty.keySet());
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            orders.add(order.withProperty(apiToEntityProperty.get(order.getProperty())));
        }
        for (Sort.Order tieBreaker : tieBreakers) {
            if (orders.stream().noneMatch(o -> o.getProperty().equals(tieBreaker.getProperty()))) {
                orders.add(tieBreaker);
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
}
