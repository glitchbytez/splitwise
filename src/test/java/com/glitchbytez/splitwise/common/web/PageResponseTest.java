package com.glitchbytez.splitwise.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void mapsContentAndCopiesPagingMetadata() {
        var page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(2, 3), 11);

        PageResponse<String> response = PageResponse.from(page, i -> "item-" + i);

        assertThat(response.content()).containsExactly("item-1", "item-2", "item-3");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(11);
    }

    @Test
    void handlesEmptyPages() {
        var page = new PageImpl<Integer>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = PageResponse.from(page, String::valueOf);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }
}
