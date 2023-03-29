package org.ujar.quarkus.restful;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.QueryParam;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies that {@code @PageableResource} endpoints have valid paging-related query param values.
 */
@PageableResource
@Interceptor
@Slf4j
@Priority(0)
public class PageableResourceInterceptor {

  private static final String PAGE_NUMBER_PARAM_NAME = "page";

  private static final String PAGE_SIZE_PARAM_NAME = "size";

  private static final int MAX_PAGE_SIZE = 100;

  private static final int MIN_PAGE_NUMBER = 0;

  /*
   * Check that the client has passed in legal values.
   */
  private static void assertParamsHaveValidValues(final Integer page, final Integer size)
      throws BadRequestException {
    // Check for valid page number.
    if (page < MIN_PAGE_NUMBER) {
      throw new BadRequestException(PAGE_NUMBER_PARAM_NAME + " must be >= " + MIN_PAGE_NUMBER);
    }

    // Check for valid page size.
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new BadRequestException(PAGE_SIZE_PARAM_NAME + " must be between 1 and " + MAX_PAGE_SIZE + " (inclusive).");
    }
  }

  /*
   * Check for missing values. This will only happen if we've forgotten to set default
   * values in openapi.yaml.
   */
  private static void assertParamsHaveValues(final Integer page, final Integer size)
      throws InternalServerErrorException {
    if (page == null || size == null) {
      final String msg = "Endpoint is pageable but is missing default " + PAGE_SIZE_PARAM_NAME + "/" + PAGE_NUMBER_PARAM_NAME + " values";
      log.error(msg);
      throw new InternalServerErrorException(msg);
    }
  }

  /*
   * Check for missing query params. This is either an error in openapi.yaml
   * (missing or misnamed query params) or @Pageable has been used with a non-paging
   * endpoint.
   */
  private static void assertMethodHasParams(final Integer pageParamIndex, final Integer sizeParamIndex) throws InternalServerErrorException {
    if (pageParamIndex == null || sizeParamIndex == null) {
      final String msg = "Endpoint is pageable but is missing %s/%s params"
          .formatted(PAGE_NUMBER_PARAM_NAME, PAGE_SIZE_PARAM_NAME);
      log.error(msg);
      throw new InternalServerErrorException(msg);
    }
  }

  /*
   * Identify which the current method's parameters is the query param we're looking for.
   */
  private static Integer getParamIndex(final String queryParamName, final Parameter... params) {
    for (int i = 0; i < params.length; ++i) {
      final Parameter p = params[i];
      final QueryParam queryParam = p.getAnnotation(QueryParam.class);
      if (queryParam != null && queryParamName.equals(queryParam.value())) {
        return i;
      }
    }
    return null;
  }

  @AroundInvoke
  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  Object validateQueryParams(final InvocationContext ctx) throws Exception {

    // Identify which parameters are the start/end parameters.
    final var method = findMethodOnInterface(ctx);
    final Integer pageParamIndex = getParamIndex(PAGE_NUMBER_PARAM_NAME, method.getParameters());
    final Integer sizeParamIndex = getParamIndex(PAGE_SIZE_PARAM_NAME, method.getParameters());

    assertMethodHasParams(pageParamIndex, sizeParamIndex);

    final Object[] params = ctx.getParameters();
    final Integer page = (Integer) params[pageParamIndex];
    final Integer size = (Integer) params[sizeParamIndex];

    assertParamsHaveValues(page, size);
    assertParamsHaveValidValues(page, size);

    return ctx.proceed();
  }

  /*
   * The RESTFul controller class must implement exactly one interface - namely the interface
   * generated by openapi-generator-maven-plugin. That interface has the JAX-RS annotations
   * on it so subsequent operations, such as locating the _start/_end parameters, need to be
   * performed on that interface and not directly on the RESTFul controller class.
   */
  private Method findMethodOnInterface(final InvocationContext context) throws NoSuchMethodException {
    final var m = context.getMethod();

    final int numOfInterfaces = m.getDeclaringClass().getInterfaces().length;
    if (numOfInterfaces == 1) {
      return m.getDeclaringClass().getInterfaces()[0].getMethod(m.getName(), m.getParameterTypes());
    } else {
      // The RESTFul controller class implements multiple interfaces. This method could be
      // updated to support that, but it's extra work that's unnecessary at the time
      // of writing.
      throw new InternalServerErrorException("RESTFul controller class must implement an auto-generated JAX-RS interface");
    }
  }
}