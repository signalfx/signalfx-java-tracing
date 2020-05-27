// Modified by SignalFx
package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;

/** Sampler that always says yes... */
public class AllSampler extends AbstractSampler {

  @Override
  public boolean doSample(final DDSpan span) {
    return true;
  }

  /** Injected sampling flags are based on sampling priority, so always keep */
  public void initializeSamplingPriority(final DDSpan span) {
    if (span.isRootSpan() || span.getSamplingPriority() == null) {
      span.setSamplingPriority(PrioritySampling.SAMPLER_KEEP);
    }
  }

  @Override
  public String toString() {
    return "AllSampler { sample=true }";
  }
}
