package com.professoros.service;

import com.professoros.model.OfferingSummary;
import java.util.Optional;

/** One instance per application window/session; no static state or persistence. UI thread owns changes. */
public final class SessionContext {
    private OfferingSummary activeOffering;
    public Optional<OfferingSummary> activeOffering() { return Optional.ofNullable(activeOffering); }
    public void select(OfferingSummary offering) { activeOffering = java.util.Objects.requireNonNull(offering); }
    public void clear() { activeOffering = null; }
}
