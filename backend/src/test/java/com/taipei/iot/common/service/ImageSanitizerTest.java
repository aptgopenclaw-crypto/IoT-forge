package com.taipei.iot.common.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ImageSanitizerTest {

	private final ImageSanitizer sanitizer = new ImageSanitizer();

	private ListAppender<ILoggingEvent> appender;

	private Logger sanitizerLogger;

	@BeforeEach
	void attachAppender() {
		sanitizerLogger = (Logger) LoggerFactory.getLogger(ImageSanitizer.class);
		appender = new ListAppender<>();
		appender.start();
		sanitizerLogger.addAppender(appender);
	}

	@AfterEach
	void detachAppender() {
		sanitizerLogger.detachAppender(appender);
	}

	@Test
	void sanitize_validJpeg_returnsSanitizedBytes() throws Exception {
		// Create a real JPEG image in memory
		BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		img.setRGB(5, 5, 0xFF0000); // set a red pixel
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, "JPEG", baos);
		byte[] original = baos.toByteArray();

		byte[] result = sanitizer.sanitize(new ByteArrayInputStream(original), "jpg");

		assertNotNull(result);
		assertTrue(result.length > 0);
		// Verify it's a valid JPEG (starts with FF D8)
		assertEquals((byte) 0xFF, result[0]);
		assertEquals((byte) 0xD8, result[1]);
	}

	@Test
	void sanitize_validPng_returnsSanitizedBytes() throws Exception {
		BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, "PNG", baos);

		byte[] result = sanitizer.sanitize(new ByteArrayInputStream(baos.toByteArray()), "png");

		assertNotNull(result);
		assertTrue(result.length > 0);
		// PNG magic bytes: 89 50 4E 47
		assertEquals((byte) 0x89, result[0]);
		assertEquals((byte) 0x50, result[1]);
	}

	@Test
	void sanitize_invalidImage_returnsNull() {
		byte[] garbage = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 };

		byte[] result = sanitizer.sanitize(new ByteArrayInputStream(garbage), "jpg");

		assertNull(result);
	}

	@Test
	void sanitize_unsupportedFormat_returnsNull() throws Exception {
		BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, "JPEG", baos);

		byte[] result = sanitizer.sanitize(new ByteArrayInputStream(baos.toByteArray()), "tiff");

		assertNull(result);
	}

	@Test
	void sanitize_stripsExifMetadata() throws Exception {
		// Create a JPEG image — after re-encode, no extra metadata should be present
		BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, "JPEG", baos);
		byte[] original = baos.toByteArray();

		byte[] sanitized = sanitizer.sanitize(new ByteArrayInputStream(original), "jpeg");

		assertNotNull(sanitized);
		// Re-decode to verify it's a valid image
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(sanitized));
		assertNotNull(decoded);
		assertEquals(100, decoded.getWidth());
		assertEquals(100, decoded.getHeight());
	}

	// ---------- N-8: multi-frame GIF detection ----------

	@Test
	void sanitizeDetailed_singleFrameGif_notDowngraded() throws Exception {
		byte[] gif = buildMultiFrameGif(1);

		ImageSanitizer.SanitizeResult result = sanitizer.sanitizeDetailed(new ByteArrayInputStream(gif), "gif");

		assertTrue(result.isSuccess());
		assertFalse(result.downgraded(), "1-frame GIF should not be flagged as downgraded");
		assertEquals(1, result.originalFrames());
		assertFalse(hasWarn("多幀圖片"), "single-frame must not emit downgrade WARN");
	}

	@Test
	void sanitizeDetailed_multiFrameGif_isFlaggedAndWarned() throws Exception {
		byte[] gif = buildMultiFrameGif(3);

		ImageSanitizer.SanitizeResult result = sanitizer.sanitizeDetailed(new ByteArrayInputStream(gif), "gif");

		assertTrue(result.isSuccess(), "should still produce a sanitized first frame");
		assertTrue(result.downgraded(), "3-frame GIF must be flagged downgraded");
		assertEquals(3, result.originalFrames());
		assertTrue(hasWarn("多幀圖片"), "downgrade must produce WARN log");
	}

	@Test
	void sanitize_multiFrameGif_logsWarnEvenViaLegacyApi() throws Exception {
		byte[] gif = buildMultiFrameGif(2);

		byte[] bytes = sanitizer.sanitize(new ByteArrayInputStream(gif), "gif");

		assertNotNull(bytes, "legacy API still returns bytes for backwards-compat");
		assertTrue(hasWarn("多幀圖片"), "legacy sanitize() must also emit WARN — N-8: no more silent downgrade");
	}

	@Test
	void sanitizeDetailed_unsupportedExtension_returnsFailureResult() {
		ImageSanitizer.SanitizeResult result = sanitizer
			.sanitizeDetailed(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "tiff");

		assertFalse(result.isSuccess());
		assertEquals(-1, result.originalFrames());
		assertFalse(result.downgraded());
	}

	// ---------- F-2: framesDropped / wasDowngraded metadata ----------

	@Test
	void sanitizeDetailed_singleFrameGif_framesDroppedIsZero() throws Exception {
		byte[] gif = buildMultiFrameGif(1);

		ImageSanitizer.SanitizeResult result = sanitizer.sanitizeDetailed(new ByteArrayInputStream(gif), "gif");

		assertEquals(0, result.framesDropped(), "single-frame source must report framesDropped == 0");
		assertFalse(result.wasDowngraded(), "wasDowngraded() must mirror downgraded() — false here");
	}

	@Test
	void sanitizeDetailed_multiFrameGif_framesDroppedEqualsFramesMinusOne() throws Exception {
		byte[] gif = buildMultiFrameGif(3);

		ImageSanitizer.SanitizeResult result = sanitizer.sanitizeDetailed(new ByteArrayInputStream(gif), "gif");

		assertEquals(2, result.framesDropped(), "3-frame source downgraded to 1 frame must report framesDropped == 2");
		assertTrue(result.wasDowngraded(), "wasDowngraded() must mirror downgraded() — true here");
		assertEquals(result.downgraded(), result.wasDowngraded(),
				"wasDowngraded() must always equal downgraded() (alias contract)");
	}

	@Test
	void sanitizeDetailed_unsupportedExtension_framesDroppedIsZero() {
		ImageSanitizer.SanitizeResult result = sanitizer
			.sanitizeDetailed(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "tiff");

		assertEquals(0, result.framesDropped(), "failure result must report framesDropped == 0 (no frames touched)");
		assertFalse(result.wasDowngraded());
	}

	@Test
	void sanitizeDetailed_multiFrameWarn_includesFramesDroppedInMessage() throws Exception {
		byte[] gif = buildMultiFrameGif(4);

		sanitizer.sanitizeDetailed(new ByteArrayInputStream(gif), "gif");

		assertTrue(
				appender.list.stream()
					.anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("framesDropped=3")),
				"downgrade WARN must surface framesDropped so callers reading logs can audit");
	}

	// ---------- helpers ----------

	private boolean hasWarn(String fragment) {
		return appender.list.stream()
			.anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(fragment));
	}

	/**
	 * Build a minimal valid GIF with the requested number of frames.
	 */
	private byte[] buildMultiFrameGif(int frameCount) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
			writer.setOutput(ios);
			writer.prepareWriteSequence(null);
			for (int i = 0; i < frameCount; i++) {
				BufferedImage frame = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
				frame.setRGB(i % 8, i % 8, 0xFF000000 | (i * 30));
				IIOMetadata md = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(frame), null);
				// Set graphic control extension so the encoder accepts a sequence
				String formatName = md.getNativeMetadataFormatName();
				IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(formatName);
				IIOMetadataNode gce = new IIOMetadataNode("GraphicControlExtension");
				gce.setAttribute("disposalMethod", "none");
				gce.setAttribute("userInputFlag", "FALSE");
				gce.setAttribute("transparentColorFlag", "FALSE");
				gce.setAttribute("delayTime", "10");
				gce.setAttribute("transparentColorIndex", "0");
				root.appendChild(gce);
				md.setFromTree(formatName, root);
				writer.writeToSequence(new IIOImage(frame, null, md), null);
			}
			writer.endWriteSequence();
		}
		finally {
			writer.dispose();
		}
		return baos.toByteArray();
	}

}
