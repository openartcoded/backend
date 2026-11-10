package tech.artcoded.websitev2.template;

/** How a template is rendered. */
public enum OaTemplateVersion {
    /** FreeMarker, rendered by the backend (html printed to pdf with chromium). Every migrated template is a v1. */
    V1,
    /** Jinja (MiniJinja), stored and rendered by the file service api. */
    V2
}
