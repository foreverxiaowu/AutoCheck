package cn.autocheck;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;

/** Shared embedded artwork for the development JAR and native Windows distribution. */
final class AppIcons {
    private static final List<Image> IMAGES = load();
    static List<Image> images() { return IMAGES; }
    private static List<Image> load() {
        try (var stream = AppIcons.class.getResourceAsStream("app-icon.png")) {
            if (stream == null) throw new IllegalStateException("应用图标资源缺失");
            BufferedImage source = ImageIO.read(stream);
            if (source == null) throw new IllegalStateException("应用图标无法读取");
            List<Image> images = new ArrayList<>();
            for (int size : new int[]{16,24,32,48,64,128,256}) {
                BufferedImage scaled = new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source,0,0,size,size,null);
                } finally { graphics.dispose(); }
                images.add(scaled);
            }
            return List.copyOf(images);
        } catch (Exception e) { throw new IllegalStateException("无法加载应用图标",e); }
    }
}
