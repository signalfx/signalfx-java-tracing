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
 *
 * <p>Modified by Splunk
 */
package org.apache.camel.opentracing;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.util.EventObject;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.opentracing.decorators.DecoratorRegistry;
import org.apache.camel.opentracing.propagation.CamelHeadersExtractAdapter;
import org.apache.camel.opentracing.propagation.CamelHeadersInjectAdapter;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To use OpenTracing with Camel then setup this {@link OpenTracingTracer} in your Camel
 * application.
 *
 * <p>This class is implemented as both an {@link org.apache.camel.spi.EventNotifier} and {@link
 * RoutePolicy} that allows to trap when Camel starts/ends an {@link Exchange} being routed using
 * the {@link RoutePolicy} and during the routing if the {@link Exchange} sends messages, then we
 * track them using the {@link org.apache.camel.spi.EventNotifier}.
 */
@ManagedResource(description = "OpenTracingTracer")
public class OpenTracingTracer extends ServiceSupport
    implements RoutePolicyFactory, StaticService, CamelContextAware {

  private static final Logger LOG = LoggerFactory.getLogger(OpenTracingTracer.class);

  private final OpenTracingEventNotifier eventNotifier = new OpenTracingEventNotifier();
  private final DecoratorRegistry registry = new DecoratorRegistry();
  private Tracer tracer;
  private CamelContext camelContext;

  public OpenTracingTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public RoutePolicy createRoutePolicy(
      CamelContext camelContext, String routeId, RouteDefinition route) {
    // ensure this opentracing tracer gets initialized when Camel starts
    init(camelContext);
    return new OpenTracingRoutePolicy(routeId);
  }

  /**
   * Registers this {@link OpenTracingTracer} on the {@link CamelContext} if not already registered.
   */
  public void init(CamelContext camelContext) {

    if (camelContext.hasService(getClass()) == null) {
      try {
        // start this service eager so we init before Camel is starting up
        camelContext.addService(this, true, true);
      } catch (Exception e) {
        throw ObjectHelper.wrapRuntimeCamelException(e);
      }
    }
  }

  @Override
  public CamelContext getCamelContext() {
    return camelContext;
  }

  @Override
  public void setCamelContext(CamelContext camelContext) {
    this.camelContext = camelContext;
  }

  public Tracer getTracer() {
    return tracer;
  }

  public void setTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  protected void doStart() throws Exception {
    ObjectHelper.notNull(camelContext, "CamelContext", this);
    ObjectHelper.notNull(tracer, "Tracer", this);

    camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
    if (!camelContext.getRoutePolicyFactories().contains(this)) {
      camelContext.addRoutePolicyFactory(this);
    }

    ServiceHelper.startServices(eventNotifier);
  }

  @Override
  protected void doStop() throws Exception {
    // stop event notifier
    camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
    ServiceHelper.stopService(eventNotifier);

    // remove route policy
    camelContext.getRoutePolicyFactories().remove(this);
  }

  protected SpanDecorator getSpanDecorator(Endpoint endpoint) {

    String component = "";
    String uri = endpoint.getEndpointUri();
    String splitURI[] = StringHelper.splitOnCharacter(uri, ":", 2);
    if (splitURI[1] != null) {
      component = splitURI[0];
    }
    SpanDecorator sd = registry.forComponent(component);
    return sd;
  }

  private final class OpenTracingEventNotifier extends EventNotifierSupport {

    @Override
    public void notify(EventObject event) throws Exception {

      try {
        /**
         * Camel about to send something (outbound). Execution stays within CAMEL, hence parent from
         * CAMEL holder.
         */
        if (event instanceof ExchangeSendingEvent) {
          ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
          SpanDecorator sd = getSpanDecorator(ese.getEndpoint());
          if (!sd.newSpan()) {
            return;
          }
          Span parent = ActiveSpanManager.getSpan(ese.getExchange());
          SpanBuilder spanBuilder =
              tracer
                  .buildSpan(sd.getOperationName(ese.getExchange(), ese.getEndpoint()))
                  .withTag(Tags.SPAN_KIND.getKey(), sd.getInitiatorSpanKind());
          // Temporary workaround to avoid adding 'null' span as a parent
          if (parent != null) {
            spanBuilder.asChildOf(parent);
          }
          Span span = spanBuilder.start();
          sd.pre(span, ese.getExchange(), ese.getEndpoint());
          tracer.inject(
              span.context(),
              Format.Builtin.TEXT_MAP,
              new CamelHeadersInjectAdapter(ese.getExchange().getIn().getHeaders()));
          ActiveSpanManager.activate(ese.getExchange(), span, tracer);

          if (LOG.isTraceEnabled()) {
            LOG.trace("OpenTracing: start client span=" + span);
          }
          /** Camel finished sending (outbound). Finish span and remove it from CAMEL holder. */
        } else if (event instanceof ExchangeSentEvent) {
          ExchangeSentEvent ese = (ExchangeSentEvent) event;
          SpanDecorator sd = getSpanDecorator(ese.getEndpoint());
          if (!sd.newSpan()) {
            return;
          }
          Span span = ActiveSpanManager.getSpan(ese.getExchange());
          if (span != null) {
            if (LOG.isTraceEnabled()) {
              LOG.trace("OpenTracing: finish client span=" + span);
            }
            sd.post(span, ese.getExchange(), ese.getEndpoint());
            ActiveSpanManager.deactivate(ese.getExchange());
          } else {
            LOG.warn("OpenTracing: could not find managed span for exchange=" + ese.getExchange());
          }
        }
      } catch (Throwable t) {
        // This exception is ignored
        LOG.warn("OpenTracing: Failed to capture tracing data", t);
      }
    }

    @Override
    public boolean isEnabled(EventObject event) {
      return event instanceof ExchangeSendingEvent || event instanceof ExchangeSentEvent;
    }

    @Override
    public String toString() {
      return "OpenTracingEventNotifier";
    }
  }

  private final class OpenTracingRoutePolicy extends RoutePolicySupport {

    OpenTracingRoutePolicy(String routeId) {}

    private Span spanOnExchangeBegin(Route route, Exchange exchange, SpanDecorator sd) {
      Span activeSpan = tracer.activeSpan();

      SpanContext parentContext =
          (activeSpan == null
              ? tracer.extract(
                  Format.Builtin.TEXT_MAP,
                  new CamelHeadersExtractAdapter(exchange.getIn().getHeaders()))
              : activeSpan.context());

      SpanBuilder builder =
          tracer
              .buildSpan(sd.getOperationName(exchange, route.getEndpoint()))
              .asChildOf(parentContext);
      if (activeSpan == null) {
        // root operation, set kind
        builder.withTag(Tags.SPAN_KIND.getKey(), sd.getReceiverSpanKind());
      }
      return builder.start();
    }

    /**
     * Route exchange started, ie request could have been already captured by upper layer
     * instrumentation. Find parent (already created takes priority over the one from CAMEL
     * headers).
     */
    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
      try {
        SpanDecorator sd = getSpanDecorator(route.getEndpoint());
        Span span = spanOnExchangeBegin(route, exchange, sd);
        sd.pre(span, exchange, route.getEndpoint());
        ActiveSpanManager.activate(exchange, span, tracer);
        if (LOG.isTraceEnabled()) {
          LOG.trace("OpenTracing: start server span=" + span);
        }
      } catch (Throwable t) {
        // This exception is ignored
        LOG.warn("OpenTracing: Failed to capture tracing data", t);
      }
    }

    /** Route exchange done. Get active CAMEL span, finish, remove from CAMEL holder. */
    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
      try {

        Span span = ActiveSpanManager.getSpan(exchange);
        if (span != null) {
          if (LOG.isTraceEnabled()) {
            LOG.trace("OpenTracing: finish server span=" + span);
          }
          SpanDecorator sd = getSpanDecorator(route.getEndpoint());
          sd.post(span, exchange, route.getEndpoint());
          ActiveSpanManager.deactivate(exchange);
        } else {
          LOG.warn("OpenTracing: could not find managed span for exchange=" + exchange);
        }
      } catch (Throwable t) {
        // This exception is ignored
        LOG.warn("OpenTracing: Failed to capture tracing data", t);
      }
    }
  }
}
