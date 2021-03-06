/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import okhttp3.ResponseBody;

final class MethodHandler {
  static MethodHandler create(Retrofit retrofit, Method method) {
    CallAdapter<?> callAdapter = createCallAdapter(method, retrofit);
    Type responseType = callAdapter.responseType();
    if (responseType == Response.class || responseType == okhttp3.Response.class) {
      throw Utils.methodError(method, "'"
          + Types.getRawType(responseType).getName()
          + "' is not a valid response body type. Did you mean ResponseBody?");
    }
    Converter<ResponseBody, ?> responseConverter =
        createResponseConverter(method, retrofit, responseType);
    RequestFactory requestFactory = RequestFactoryParser.parse(method, responseType, retrofit);
    return new MethodHandler(retrofit.callFactory(), requestFactory, callAdapter,
        responseConverter);
  }

  private static CallAdapter<?> createCallAdapter(Method method, Retrofit retrofit) {
    Type returnType = method.getGenericReturnType();
    if (Utils.hasUnresolvableType(returnType)) {
      throw Utils.methodError(method,
          "Method return type must not include a type variable or wildcard: %s", returnType);
    }
    if (returnType == void.class) {
      throw Utils.methodError(method, "Service methods cannot return void.");
    }
    Annotation[] annotations = method.getAnnotations();
    try {
      return retrofit.callAdapter(returnType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw Utils.methodError(e, method, "Unable to create call adapter for %s", returnType);
    }
  }

  private static Converter<ResponseBody, ?> createResponseConverter(Method method,
      Retrofit retrofit, Type responseType) {
    Annotation[] annotations = method.getAnnotations();
    try {
      return retrofit.responseBodyConverter(responseType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw Utils.methodError(e, method, "Unable to create converter for %s", responseType);
    }
  }

  private final okhttp3.Call.Factory callFactory;
  private final RequestFactory requestFactory;
  private final CallAdapter<?> callAdapter;
  private final Converter<ResponseBody, ?> responseConverter;

  private MethodHandler(okhttp3.Call.Factory callFactory, RequestFactory requestFactory,
      CallAdapter<?> callAdapter, Converter<ResponseBody, ?> responseConverter) {
    this.callFactory = callFactory;
    this.requestFactory = requestFactory;
    this.callAdapter = callAdapter;
    this.responseConverter = responseConverter;
  }

  Object invoke(Object... args) {
    return callAdapter.adapt(
        new OkHttpCall<>(callFactory, requestFactory, args, responseConverter));
  }
}
