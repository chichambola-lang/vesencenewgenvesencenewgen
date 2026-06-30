package vesence.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Environment(EnvType.CLIENT)
public final class VideoBackground {

    private final File file;
    private final double fps;
    private volatile boolean running = false;
    private Thread decodeThread;

    private final BlockingQueue<Frame> frameQueue = new ArrayBlockingQueue<>(4);

    private int textureId = 0;
    private int texW = 0, texH = 0;
    private long lastUploadNanos = 0L;

    private static final class Frame {
        final int w, h;
        final ByteBuffer rgba;
        Frame(int w, int h, ByteBuffer rgba) { this.w = w; this.h = h; this.rgba = rgba; }
    }

    public VideoBackground(File file, double fps) {
        this.file = file;
        this.fps = fps <= 0 ? 30.0 : fps;
    }

    public boolean isAvailable() {
        return file != null && file.isFile();
    }

    public void start() {
        if (running || !isAvailable()) return;
        running = true;
        decodeThread = new Thread(this::decodeLoop, "Vesence-VideoBg");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    public void stop() {
        running = false;
        if (decodeThread != null) {
            decodeThread.interrupt();
            decodeThread = null;
        }
        frameQueue.clear();
    }

    private void decodeLoop() {
        long frameIntervalMs = (long) (1000.0 / fps);
        while (running) {
            SeekableByteChannel ch = null;
            try {
                ch = NIOUtils.readableChannel(file);
                FrameGrab grab = FrameGrab.createFrameGrab(ch);
                Picture picture;
                while (running && (picture = grab.getNativeFrame()) != null) {
                    long t0 = System.currentTimeMillis();
                    Frame frame = toRgbaFrame(picture);
                    if (frame != null) {

                        if (!frameQueue.offer(frame)) {
                            frameQueue.poll();
                            frameQueue.offer(frame);
                        }
                    }
                    long elapsed = System.currentTimeMillis() - t0;
                    long sleep = frameIntervalMs - elapsed;
                    if (sleep > 0) {
                        try { Thread.sleep(sleep); } catch (InterruptedException e) { return; }
                    }
                }
            } catch (Throwable t) {
                System.err.println("[Vesence] VideoBackground decode error: " + t.getMessage());

                try { Thread.sleep(250); } catch (InterruptedException e) { return; }
            } finally {
                if (ch != null) {
                    try { NIOUtils.closeQuietly(ch); } catch (Throwable ignored) {}
                }
            }

        }
    }

    private Frame toRgbaFrame(Picture src) {
        try {
            Picture rgb = Picture.create(src.getWidth(), src.getHeight(), org.jcodec.common.model.ColorSpace.RGB);
            org.jcodec.scale.ColorUtil.getTransform(src.getColor(), org.jcodec.common.model.ColorSpace.RGB)
                    .transform(src, rgb);
            int w = rgb.getWidth();
            int h = rgb.getHeight();
            byte[] data = rgb.getPlaneData(0);
            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());

            for (int row = h - 1; row >= 0; row--) {
                int base = row * w * 3;
                for (int col = 0; col < w; col++) {
                    int i = base + col * 3;
                    buf.put((byte) (data[i] + 128));
                    buf.put((byte) (data[i + 1] + 128));
                    buf.put((byte) (data[i + 2] + 128));
                    buf.put((byte) 255);
                }
            }
            buf.flip();
            return new Frame(w, h, buf);
        } catch (Throwable t) {
            return null;
        }
    }

    public int updateAndGetTexture() {
        Frame frame = frameQueue.poll();
        if (frame == null) {
            return textureId;
        }

        if (textureId == 0) {
            textureId = GL11.glGenTextures();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        if (frame.w != texW || frame.h != texH) {
            texW = frame.w;
            texH = frame.h;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, texW, texH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frame.rgba);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, texW, texH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frame.rgba);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        lastUploadNanos = System.nanoTime();
        return textureId;
    }

    public void deleteTexture() {
        if (textureId != 0) {
            try { GL11.glDeleteTextures(textureId); } catch (Throwable ignored) {}
            textureId = 0;
            texW = 0;
            texH = 0;
        }
    }
}
