package com.gameryt;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

public class MainWindow extends JPanel {
    private static String videoPath = "src/main/resources/music/Touhou - Bad Apple.mp4";
    private static final String ASCII_CHARS = "@#S%?*+;:,.";
    private static final int ASCII_WIDTH = 160;
    private static final int ASCII_HEIGHT = 90;
    private static final boolean SKIP_TIME = false;
    private static final boolean DEBUG_MODE = false;
    private BufferedImage frameImage;

    public static void main(String[] args) {
        if(!Files.exists(Path.of(videoPath)))
            videoPath = args[0];
        JFrame frame = new JFrame("ASCII Video Player");
        MainWindow player = new MainWindow();
        frame.add(player);
        frame.setSize(800, 500);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public MainWindow() {
        new Thread(this::processMedia).start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (frameImage != null) {
            g.drawImage(frameImage, 0, 0, getWidth(), getHeight(), null);
        }
    }

    public void processMedia() {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new File(videoPath))) {
            grabber.start();
            Java2DFrameConverter converter = new Java2DFrameConverter();

            AudioFormat audioFormat = new AudioFormat(grabber.getSampleRate(), 16, grabber.getAudioChannels(), true, true);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(audioFormat);
            audioLine.start();

            long offset = skipTime(grabber);
            long startTime = getCurrentTimesInMicro() - offset;

            playMidia(grabber, converter, audioLine, startTime);

            audioLine.drain();
            audioLine.close();
            grabber.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static long skipTime(FFmpegFrameGrabber grabber) throws FFmpegFrameGrabber.Exception {
        if (!SKIP_TIME)
            return 0;
        long offset = grabber.getLengthInTime() - Duration.of(10, ChronoUnit.SECONDS).toMillis() * 1000;
        grabber.setTimestamp(offset, true);
        return offset;
    }

    private void playMidia(FFmpegFrameGrabber grabber, Java2DFrameConverter converter, SourceDataLine audioLine, long startTime) throws FFmpegFrameGrabber.Exception, InterruptedException {
        Frame frame;
        while ((frame = grabber.grab()) != null) {
            debugPrint("Frame timestamp: " + grabber.getTimestamp());
            if (frame.image != null) {
                BufferedImage image = converter.convert(frame);
                frameImage = convertToASCIIImage(image);
                repaint();
            }
            if (frame.samples != null) {
                ShortBuffer channelSamples = (ShortBuffer) frame.samples[0];
                channelSamples.rewind();
                ByteBuffer outBuffer = ByteBuffer.allocate(channelSamples.capacity() * 2);
                for (int i = 0; i < channelSamples.capacity(); i++) {
                    outBuffer.putShort(channelSamples.get(i));
                }
                audioLine.write(outBuffer.array(), 0, outBuffer.capacity());
            }

            long frameTimestamp = frame.timestamp;
            long currentTime = getCurrentTimesInMicro();
            long elapsedTime = currentTime - startTime;
            long sleepTime = frameTimestamp - elapsedTime;
            if (sleepTime > 0) {
                long sleepMillis = sleepTime / 1000;
                int sleepNanos = (int) ((sleepTime % 1000) * 1000);
                Thread.sleep(sleepMillis, sleepNanos);
            }
        }
    }

    private static long getCurrentTimesInMicro() {
        return System.nanoTime() / 1000;
    }

    private BufferedImage convertToASCIIImage(BufferedImage image) {
        BufferedImage resized = new BufferedImage(MainWindow.ASCII_WIDTH, MainWindow.ASCII_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(image, 0, 0, MainWindow.ASCII_WIDTH, MainWindow.ASCII_HEIGHT, null);
        g.dispose();

        int threshold = calculateAdaptiveThreshold(resized);

        BufferedImage asciiImage = new BufferedImage(MainWindow.ASCII_WIDTH * 6, MainWindow.ASCII_HEIGHT * 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = asciiImage.createGraphics();
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, asciiImage.getWidth(), asciiImage.getHeight());
        g2d.setColor(Color.BLACK);

        int[] pixelCache = new int[MainWindow.ASCII_WIDTH * MainWindow.ASCII_HEIGHT];
        resized.getRGB(0, 0, MainWindow.ASCII_WIDTH, MainWindow.ASCII_HEIGHT, pixelCache, 0, MainWindow.ASCII_WIDTH);

        for (int y = 0; y < MainWindow.ASCII_HEIGHT; y++) {
            for (int x = 0; x < MainWindow.ASCII_WIDTH; x++) {
                int pixel = pixelCache[y * MainWindow.ASCII_WIDTH + x] & 0xFF;
                if (pixel < threshold) {
                    int index = (pixel * (ASCII_CHARS.length() - 1)) / 255;
                    String character = String.valueOf(ASCII_CHARS.charAt(index));
                    g2d.drawString(character, x * 6, y * 10);
                }
            }
        }
        g2d.dispose();
        return asciiImage;
    }

    private int calculateAdaptiveThreshold(BufferedImage image) {
        int sum = 0;
        int count = image.getWidth() * image.getHeight();
        int[] pixelCache = new int[count];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixelCache, 0, image.getWidth());
        for (int pixel : pixelCache) {
            sum += pixel & 0xFF;
        }
        return sum / count;
    }

    private static void debugPrint(String message) {
        if (DEBUG_MODE) {
            Logger.getGlobal().info(message);
        }
    }
}
