package tech.artcoded.websitev2.pages.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LabelServiceTest {
    private LabelRepository repository;
    private LabelService service;

    @BeforeEach
    void setUp() {
        repository = mock(LabelRepository.class);
        service = new LabelService(repository);
        when(repository.save(any(Label.class))).thenAnswer(a -> a.getArgument(0));
    }

    private Label saved() {
        var captor = ArgumentCaptor.forClass(Label.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void namesAreTrimmedAndUpperCased() {
        service.saveAll(List.of(Label.builder().name("  travel ").colorHex("#abc").priceHVAT(BigDecimal.TEN)
                .vat(BigDecimal.ONE).description("desc").hidden(true).build()));
        var label = saved();
        assertThat(label.getName()).isEqualTo("TRAVEL");
        assertThat(label.getColorHex()).isEqualTo("#abc");
        assertThat(label.getPriceHVAT()).isEqualByComparingTo("10");
        assertThat(label.isHidden()).isTrue();
        assertThat(label.getDescription()).isEqualTo("desc");
    }

    @Test
    void existingLabelWithSameNameIsUpdatedInsteadOfDuplicated() {
        var existing = Label.builder().id("existing-id").name("TRAVEL").colorHex("#000").build();
        when(repository.findByNameIgnoreCase("TRAVEL")).thenReturn(Optional.of(existing));
        service.saveAll(List.of(Label.builder().id("new-id").name("travel").colorHex("#fff").build()));
        var label = saved();
        assertThat(label.getId()).isEqualTo("existing-id");
        assertThat(label.getColorHex()).isEqualTo("#fff");
    }

    @Test
    void sameColorForAnotherLabelIsRejected() {
        when(repository.findByColorHex("#fff"))
                .thenReturn(Optional.of(Label.builder().name("FOOD").colorHex("#fff").build()));
        assertThatThrownBy(() -> service.saveAll(List.of(Label.builder().name("travel").colorHex("#fff").build())))
                .hasMessageContaining("same label twice");
        verify(repository, never()).save(any());
    }

    @Test
    void sameColorForTheSameLabelIsAccepted() {
        when(repository.findByColorHex("#fff"))
                .thenReturn(Optional.of(Label.builder().name("TRAVEL").colorHex("#fff").build()));
        service.saveAll(List.of(Label.builder().name("travel").colorHex("#fff").build()));
        assertThat(saved().getName()).isEqualTo("TRAVEL");
    }

    @Test
    void invalidLabelsAreRejected() {
        assertThatThrownBy(() -> service.saveAll(List.of(Label.builder().name("  ").colorHex("#fff").build())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Name");
        assertThatThrownBy(() -> service.saveAll(List.of(Label.builder().name(null).colorHex("#fff").build())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Name");
        assertThatThrownBy(() -> service.saveAll(List.of(Label.builder().name("x").colorHex("red").build())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Color");
        assertThatThrownBy(() -> service.saveAll(List.of(Label.builder().name("x").colorHex(null).build())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Color");
        verify(repository, never()).save(any());
    }

    @Test
    void emptinessAndLookups() {
        when(repository.count()).thenReturn(0L);
        assertThat(service.isEmpty()).isTrue();
        when(repository.count()).thenReturn(3L);
        assertThat(service.isEmpty()).isFalse();
        var label = Label.builder().name("X").build();
        when(repository.findAll()).thenReturn(List.of(label));
        when(repository.findByNameIgnoreCase("x")).thenReturn(Optional.of(label));
        assertThat(service.findAll()).containsExactly(label);
        assertThat(service.findByName("x")).contains(label);
    }
}
