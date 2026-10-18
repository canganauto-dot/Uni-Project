package com.professoros.pdf;

import com.professoros.model.DomainValidationException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;

final class PdfImages {
    private PdfImages() { }
    static BufferedImage read(String path,String label) throws IOException {
        Path file;
        try { file=Path.of(path); } catch(InvalidPathException invalid) { throw new DomainValidationException(label+" path is invalid."); }
        if(!file.isAbsolute() || !Files.isRegularFile(file)) {
            throw new DomainValidationException(label+" is missing or not an absolute local file. Restore the image before exporting: "+path);
        }
        if(Files.size(file)>10*1024*1024) { throw new DomainValidationException(label+" exceeds the 10 MB image limit."); }
        try(var input=ImageIO.createImageInputStream(file.toFile())) {
            if(input==null) { throw new DomainValidationException(label+" cannot be read."); }
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) { throw new DomainValidationException(label+" must be a readable PNG or JPEG image."); }
            var reader=readers.next();
            try {
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("jpg")) {
                    throw new DomainValidationException(label+" must be PNG or JPEG.");
                }
                reader.setInput(input,true,true);
                if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000) { throw new DomainValidationException(label+" exceeds 20 megapixels."); }
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}
