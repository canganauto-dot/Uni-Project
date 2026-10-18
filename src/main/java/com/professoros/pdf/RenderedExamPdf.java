package com.professoros.pdf;
import java.util.List;
public record RenderedExamPdf(byte[] bytes, PdfKind kind, int pageCount, List<String> warnings, boolean marksMismatch) {
    public RenderedExamPdf { bytes=bytes.clone(); warnings=List.copyOf(warnings); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
