package tech.artcoded.websitev2.pages.fee;

import static com.google.common.base.Preconditions.checkArgument;
import static org.codehaus.plexus.util.StringUtils.isNotEmpty;
import static tech.artcoded.websitev2.utils.common.HexColorUtils.isValid;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

@Service
public class LabelService {

    private final LabelRepository labelRepository;

    public LabelService(LabelRepository labelRepository) {
        this.labelRepository = labelRepository;
    }

    @CachePut(cacheNames = "labelsTAG", key = "'$allTags$'")
    public List<Label> findAll() {
        return labelRepository.findAll();
    }

    public boolean isEmpty() {
        return labelRepository.count() == 0;
    }

    // @CachePut(cacheNames = "labelsTAG", key = "#p0")
    public Optional<Label> findByName(String name) {
        return labelRepository.findByNameIgnoreCase(name);
    }

    @CacheEvict(cacheNames = "labelsTAG", allEntries = true)
    public void saveAll(List<Label> labels) {
        labels.forEach(this::save);
    }

    private Label save(Label label) {
        String colorHex = label.getColorHex();
        String name = label.getName().trim().toUpperCase(Locale.ROOT);
        checkArgument(isNotEmpty(name), "Name cannot be empty!! ");
        checkArgument(isNotEmpty(colorHex) && isValid(colorHex), "Color must be valid!");
        Optional<Label> byColorHex = labelRepository.findByColorHex(colorHex);
        Optional<Label> byNameIgnoreCase = labelRepository.findByNameIgnoreCase(name);
        if (byColorHex.isPresent() && !byColorHex.get().getName().equalsIgnoreCase(name)) {
            throw new RuntimeException("Cannot have the same label twice!");
        }
        var toUpdate = byNameIgnoreCase.map(Label::toBuilder).orElseGet(label::toBuilder).name(name).colorHex(colorHex)
                // .noDefaultPrice(label.isNoDefaultPrice()) TODO could
                // be useful to add labels without a price for filtering
                .priceHVAT(label.getPriceHVAT()).vat(label.getVat()).description(label.getDescription()).build();
        return labelRepository.save(toUpdate);
    }
}
