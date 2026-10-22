package com.glitchbytez.splitwise.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.glitchbytez.splitwise.common.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageablesTest {

    private static final Map<String, String> MAPPING = Map.of("date", "expenseDate", "amount", "amountMinor");

    @Test
    void translatesApiNamesToEntityPropertiesAndKeepsDirectionAndPaging() {
        Pageable result = Pageables.translateSort(PageRequest.of(2, 15, Sort.by(Sort.Order.asc("amount"))), MAPPING);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(15);
        assertThat(result.getSort()).containsExactly(Sort.Order.asc("amountMinor"));
    }

    @Test
    void appendsTieBreakersThatAreNotAlreadySorted() {
        Pageable result = Pageables.translateSort(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("date"))), MAPPING,
                Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

        assertThat(result.getSort()).containsExactly(
                Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    void rejectsPropertiesOutsideTheMapping() {
        assertThatThrownBy(() -> Pageables.translateSort(PageRequest.of(0, 20, Sort.by("amountMinor")), MAPPING))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot sort by 'amountMinor'. Allowed: amount, date");
    }
}
