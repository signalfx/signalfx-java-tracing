/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for managing active spans as a stack associated with an exchange. */
public final class ActiveSpanManager {

  private static final String ACTIVE_SPAN_PROPERTY = "OpenTracing.activeSpan";

  private static final Logger LOG = LoggerFactory.getLogger(ActiveSpanManager.class);

  private ActiveSpanManager() {}

  /**
   * This method returns the current active span associated with the exchange.
   *
   * @param exchange The exchange
   * @return The current active span, or null if none exists
   */
  public static Span getSpan(Exchange exchange) {
    SpanWithScope spanWithScope = (SpanWithScope) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
    if (spanWithScope != null) {
      return spanWithScope.getSpan();
    }
    return null;
  }

  /**
   * This method activates the supplied span for the supplied exchange. If an existing span is found
   * for the exchange, this will be pushed onto a stack.
   *
   * @param exchange The exchange
   * @param span The span
   */
  public static void activate(Exchange exchange, Span span, Tracer tracer) {

    SpanWithScope parent = (SpanWithScope) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
    SpanWithScope spanWithScope = SpanWithScope.activate(span, parent, tracer);
    exchange.setProperty(ACTIVE_SPAN_PROPERTY, spanWithScope);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Activated span: " + spanWithScope);
    }
  }

  /**
   * This method deactivates an existing active span associated with the supplied exchange. Once
   * deactivated, if a parent span is found associated with the stack for the exchange, it will be
   * restored as the current span for that exchange.
   *
   * @param exchange The exchange
   */
  public static void deactivate(Exchange exchange) {

    SpanWithScope spanWithScope = (SpanWithScope) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
    if (spanWithScope != null) {
      spanWithScope.deactivate();
      exchange.setProperty(ACTIVE_SPAN_PROPERTY, spanWithScope.getParent());
      if (LOG.isTraceEnabled()) {
        LOG.trace("Deactivated span: " + spanWithScope);
      }
    }
  }

  /**
   * Simple holder for the currently active span and an optional reference to the parent holder.
   * This will be used to maintain a stack for spans, built up during the execution of a series of
   * chained camel exchanges, and then unwound when the responses are processed.
   */
  public static class SpanWithScope {
    private SpanWithScope parent;
    private Span span;
    private Scope scope;

    public SpanWithScope(SpanWithScope parent, Span span, Scope scope) {
      this.parent = parent;
      this.span = span;
      this.scope = scope;
    }

    public static SpanWithScope activate(Span span, SpanWithScope parent, Tracer tracer) {
      Scope scope = tracer.activateSpan(span);
      return new SpanWithScope(parent, span, scope);
    }

    public SpanWithScope getParent() {
      return parent;
    }

    public Span getSpan() {
      return span;
    }

    public void deactivate() {
      span.finish();
      scope.close();
    }

    @Override
    public String toString() {
      return "SpanWithScope [span=" + span + ", scope=" + scope + "]";
    }
  }
}
