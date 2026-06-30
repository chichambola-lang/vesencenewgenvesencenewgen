package vesence.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.Transform;
import org.jcodec.scale.Yuv420jToRgb;
import org.jcodec.scale.Yuv420pToRgb;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class VideoPlayer {

    private static final int   FRAME_QUEUE_SIZE = 6;
    private static final float DEFAULT_FPS      = 30f;

    private int     glTextureId = -1;
    private boolean initialized = false;
    private boolean looping     = true;

    private final AtomicBoolean running    = new AtomicBoolean(false);
    private final BlockingQueue<FrameData> frameQueue = new ArrayBlockingQueue<>(FRAME_QUEUE_SIZE);

    private Thread decodeThread;
    private Path   tempVideoPath;

    private int  videoWidth    = 0;
    private int  videoHeight   = 0;
    private long lastFrameTime = 0;
    private final long frameIntervalMs;

    public VideoPlayer(float fps) {
        this.frameIntervalMs = (long)(1000f / fps);
    }

    public VideoPlayer() {
        this(DEFAULT_FPS);
    }

    public void init(String resourcePath) {
        if (initialized) return;
        try {
            System.out.println("[VideoPlayer] Looking for resource: " + resourcePath);
            InputStream is = VideoPlayer.class.getResourceAsStream(resourcePath);
            if (is == null) {

                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                        resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
            }
            if (is == null) {
                System.err.println("[VideoPlayer] Resource not found: " + resourcePath);
                return;
            }
            System.out.println("[VideoPlayer] Resource found, copying to temp file...");

            tempVideoPath = Files.createTempFile("vesence_bg_", ".mp4");
            tempVideoPath.toFile().deleteOnExit();
            long bytes = Files.copy(is, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);
            is.close();
            System.out.println("[VideoPlayer] Copied " + bytes + " bytes to " + tempVideoPath);

            glTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            initialized = true;
            startDecodeThread();

        } catch (Exception e) {
            System.err.println("[VideoPlayer] Init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startDecodeThread() {
        running.set(true);
        decodeThread = new Thread(() -> {
            while (running.get()) {
                try {
                    decodeLoop();
                } catch (Exception e) {
                    if (running.get()) {
                        System.err.println("[VideoPlayer] Decode error: " + e.getMessage());
                    }
                }
                if (!looping || !running.get()) break;
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Vesence-VideoDecoder");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    private void decodeLoop() throws IOException, JCodecException {
        System.out.println("[VideoPlayer] Starting decode loop: " + tempVideoPath);
        FrameGrab grab = FrameGrab.createFrameGrab(
                NIOUtils.readableChannel(tempVideoPath.toFile()));

        Transform yuv420jToRgb = new Yuv420jToRgb();
        Transform yuv420pToRgb = new Yuv420pToRgb();
        int frameCount = 0;

        while (running.get()) {
            Picture src = grab.getNativeFrame();
            if (src == null) {
                System.out.println("[VideoPlayer] End of video after " + frameCount + " frames");
                break;
            }
            frameCount++;
            if (frameCount == 1) {
                System.out.println("[VideoPlayer] First frame: " + src.getWidth() + "x" + src.getHeight()
                        + " color=" + src.getColor());
            }

            int w = src.getWidth();
            int h = src.getHeight();

            Picture rgb = Picture.create(w, h, ColorSpace.RGB);
            ColorSpace cs = src.getColor();
            if (cs == ColorSpace.YUV420J) {
                yuv420jToRgb.transform(src, rgb);
            } else if (cs == ColorSpace.YUV420 || cs == ColorSpace.YUV422) {
                yuv420pToRgb.transform(src, rgb);
            } else if (cs == ColorSpace.RGB) {
                rgb = src;
            } else {

                try { yuv420pToRgb.transform(src, rgb); }
                catch (Exception ignored) { continue; }
            }

            byte[] plane = rgb.getPlaneData(0);
            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
            for (int i = 0; i < w * h; i++) {
                int base = i * 3;
                buf.put((byte)(plane[base]     + 128));
                buf.put((byte)(plane[base + 1] + 128));
                buf.put((byte)(plane[base + 2] + 128));
                buf.put((byte) 0xFF);
            }
            buf.flip();

            try {
                frameQueue.put(new FrameData(buf, w, h));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void update() {
        if (!initialized || glTextureId < 0) return;

        long now = System.currentTimeMillis();
        if (now - lastFrameTime < frameIntervalMs) return;
        lastFrameTime = now;

        FrameData frame = frameQueue.poll();
        if (frame == null) return;

        videoWidth  = frame.width;
        videoHeight = frame.height;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                frame.width, frame.height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frame.data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public int     getGlTextureId() { return glTextureId; }
    public int     getWidth()       { return videoWidth;  }
    public int     getHeight()      { return videoHeight; }
    public boolean isReady()        { return initialized && glTextureId >= 0; }

    public void destroy() {
        running.set(false);
        if (decodeThread != null) {
            decodeThread.interrupt();
            decodeThread = null;
        }
        frameQueue.clear();
        if (glTextureId >= 0) {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = -1;
        }
        if (tempVideoPath != null) {
            try { Files.deleteIfExists(tempVideoPath); } catch (Exception ignored) {}
            tempVideoPath = null;
        }
        initialized = false;
        videoWidth  = 0;
        videoHeight = 0;
    }

    private static class FrameData {
        final ByteBuffer data;
        final int width, height;
        FrameData(ByteBuffer data, int width, int height) {
            this.data = data; this.width = width; this.height = height;
        }
    }
}
