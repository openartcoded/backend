package tech.artcoded.websitev2.upload;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.Getter;

// needed for mongock. apparently it can't inject a list of I

@Component
public class LinkableWrapperBean {
    @Getter
    private final List<ILinkable> linkables;

    public LinkableWrapperBean(List<ILinkable> linkables) {
        this.linkables = linkables;
    }

}
