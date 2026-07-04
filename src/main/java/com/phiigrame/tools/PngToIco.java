package com.phiigrame.tools;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny command-line helper that converts a PNG file into a multi-size
 * Windows {@code .ico} file.  Each size is embedded as a PNG inside the
 * ICO container (Vista+ ICO format), so we don't need to implement a
 * BMP encoder.
 *
 * <p>Usage: {@code java -cp <classpath> com.phiigrame.tools.PngToIco
 * <input.png> <output.ico> [size1 size2 ...]}
 */
public class PngToIco {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: PngToIco <input.png> <output.ico> [sizes...]");
            System.exit(2);
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);

        int[] sizes;
        if (args.length > 2) {
            sizes = new int[args.length - 2];
            for (int i = 0; i < sizes.length; i++) sizes[i] = Integer.parseInt(args[i + 2]);
        } else {
            sizes = new int[]{16, 24, 32, 48, 64, 128, 256};
        }

        BufferedImage src = ImageIO.read(in.toFile());
        if (src == null) throw new IOException("Could not read " + in);

        // Render each size, encode as PNG, collect.
        List<byte[]> pngs = new ArrayList<>(sizes.length);
        for (int s : sizes) {
            BufferedImage r = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = r.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src.getScaledInstance(s, s, Image.SCALE_SMOOTH), 0, 0, s, s, null);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(r, "png", baos);
            pngs.add(baos.toByteArray());
        }

        // ICONDIR (6 bytes) + ICONDIRENTRY (16 bytes per image) + data
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        out2.write(0); out2.write(0);                       // reserved
        out2.write(1); out2.write(0);                       // type 1 = .ico
        out2.write(sizes.length & 0xFF);
        out2.write((sizes.length >>> 8) & 0xFF);            // # images

        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            byte w = (s >= 256) ? 0 : (byte) s;
            byte h = (s >= 256) ? 0 : (byte) s;
            out2.write(w & 0xFF);                           // width  (0 = 256)
            out2.write(h & 0xFF);                           // height (0 = 256)
            out2.write(0);                                  // palette
            out2.write(0);                                  // reserved
            out2.write(1); out2.write(0);                   // planes
            out2.write(32); out2.write(0);                  // bpp
            byte[] data = pngs.get(i);
            out2.write(data.length & 0xFF);
            out2.write((data.length >>> 8) & 0xFF);
            out2.write((data.length >>> 16) & 0xFF);
            out2.write((data.length >>> 24) & 0xFF);        // size
            out2.write(offset & 0xFF);
            out2.write((offset >>> 8) & 0xFF);
            out2.write((offset >>> 16) & 0xFF);
            out2.write((offset >>> 24) & 0xFF);              // offset
            offset += data.length;
        }
        for (byte[] d : pngs) out2.write(d);

        Files.write(out, out2.toByteArray());
        System.out.println("Wrote " + out + " (" + out2.size() + " bytes, " + sizes.length + " sizes)");
    }
}
