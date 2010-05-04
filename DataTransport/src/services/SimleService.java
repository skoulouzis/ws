/*
   Copyright 2009 S. Koulouzis

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  
 */

package services;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

/**
 *
 * @author alogo
 */
public class SimleService {
        // String buffer used to gather the result 
    private static final StringBuffer result = new StringBuffer();

    // The first result is printed differently. 
    private static boolean first = true;
    

    public void compressJpegFile(String infileLocation, String outfileLocation, float compressionQuality) {
        try {
            File infile = new File(infileLocation);
            File outfile = new File(outfileLocation);
            // Retrieve jpg image to be compressed
            RenderedImage rendImage = ImageIO.read(infile);

            // Find a jpeg writer
            ImageWriter writer = null;
            Iterator iter = ImageIO.getImageWritersByFormatName("jpg");
            if (iter.hasNext()) {
                writer = (ImageWriter) iter.next();
            }

            // Prepare output file
            ImageOutputStream ios = ImageIO.createImageOutputStream(outfile);
            writer.setOutput(ios);

            // Set the compression quality
            ImageWriteParam iwparam = new MyImageWriteParam();
            iwparam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            iwparam.setCompressionQuality(compressionQuality);

            // Write the image
            writer.write(null, new IIOImage(rendImage, null, null), iwparam);

            // Cleanup
            ios.flush();
            writer.dispose();
            ios.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a partial result (times * factor) to the result buffer.
     * 
     * @param times the number of times the factor should be added
     * @param factor the factor found
     */
    private void foundFactor(int times, long factor) {
        if (!first) {
            result.append(" * ");
        } else {
            first = false;
        }

        for (int i = 0; i < times; i++) {
            result.append(factor);

            if (i != times - 1) {
                result.append(" * ");
            }
        }
    }
    
    public String factor(long n) { 

        for (long i = 2; i <= n; i++) {
            if ((n % i) == 0) {
                if (isPrime(i)) {
                    long nn = n;
                    int times = 0;
                    while ((nn % i) == 0) {
                        nn /= i;
                        times++;
                    }
                    foundFactor(times, i);
                }
            }
        }
        return result.toString();
    }
    
    private boolean isPrime(long n) {

        if (n == 2) {
            return true;
        }

        if ((n % 2) == 0) {
            return false;
        }

        long sqrn = (long) Math.sqrt(n) + 1;

        for (long i = 3; i <= sqrn; i += 2) {
            if ((n % i) == 0) {
                return false;
            }
        }

        return true;
    }
}


// This class overrides the setCompressionQuality() method to workaround
// a problem in compressing JPEG images using the javax.imageio package.
class MyImageWriteParam extends JPEGImageWriteParam {

    public MyImageWriteParam() {
        super(Locale.getDefault());
    }

    // This method accepts quality levels between 0 (lowest) and 1 (highest) and simply converts
    // it to a range between 0 and 256; this is not a correct conversion algorithm.
    // However, a proper alternative is a lot more complicated.
    // This should do until the bug is fixed.
    public void setCompressionQuality(float quality) {
        if (quality < 0.0F || quality > 1.0F) {
            throw new IllegalArgumentException("Quality out-of-bounds!");
        }
        this.compressionQuality = 256 - (quality * 256);
    }
    }

