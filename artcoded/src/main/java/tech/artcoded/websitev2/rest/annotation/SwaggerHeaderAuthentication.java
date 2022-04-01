package tech.artcoded.websitev2.rest.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
//@ApiImplicitParams({
//  @ApiImplicitParam(name = "X-AUTH-TOKEN", value = "Access Token", paramType = "header"),
//  @ApiImplicitParam(
//          name = "Authorization",
//          value = "Access Token",
//          example = "Basic YWRtaW46MTIzNA==",
//          paramType = "header")
//})
public @interface SwaggerHeaderAuthentication {
}
