package com.taipei.iot.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 圖片消毒 — 透過 ImageIO re-encode 消除 EXIF metadata 及 Polyglot payload。
 * <p>
 * 原理：{@code ImageIO.read()} 解析圖片像素後， {@code ImageIO.write()} 只輸出純像素資料， 任何嵌入的
 * EXIF、XMP、Polyglot 內容都會被丟棄。
 * </p>
 *
 * <h3>N-8 / F-2 防護（2026-05-27 / 2026-05-28）：多幀動圖偵測 + 結構化結果</h3>
 * <p>
 * {@code ImageIO.read()} 只解出第一幀；多幀 GIF / APNG 會被「降級」為靜態圖且呼叫端 無從得知。本實作改為：
 * </p>
 * <ul>
 * <li>消毒前先以 {@link ImageReader#getNumImages(boolean) getNumImages(true)} 取得幀數。</li>
 * <li>提供 {@link #sanitizeDetailed(InputStream, String)} 回傳 {@link SanitizeResult}， 帶
 * {@code originalFrames} / {@code wasDowngraded} / {@code framesDropped} 旗標，
 * 呼叫端可決定提示使用者或拒絕。</li>
 * <li>既有 {@link #sanitize(InputStream, String)} 維持回傳 {@code byte[]} 不破壞呼叫端， 但在偵測到多幀時會
 * {@code log.warn} 不再靜默。</li>
 * </ul>
 * <p>
 * <b>已知限制</b>：標準 JDK ImageIO 對 APNG 不認得 {@code acTL} chunk，多幀 APNG 仍會回
 * {@code numImages == 1}，無法在此層偵測；需在更上層用 magic bytes 識別。
 * </p>
 */
@Slf4j
@Component
public class ImageSanitizer {

	/**
	 * 消毒結果（F-2）。
	 *
	 * @param bytes 消毒後的圖片位元組；失敗為 {@code null}
	 * @param originalFrames 來源圖片偵測到的幀數；無法判定時為 {@code -1}
	 * @param wasDowngraded 是否因多幀來源被降級為單幀靜態圖
	 * @param framesDropped 因降級而被丟棄的幀數（{@code wasDowngraded} 為 false 時恆為 0）
	 */
	public record SanitizeResult(byte[] bytes, int originalFrames, boolean wasDowngraded, int framesDropped) {
		public boolean isSuccess() {
			return bytes != null;
		}

		/**
		 * 兼容舊呼叫端的 alias；等同 {@link #wasDowngraded()}。
		 */
		public boolean downgraded() {
			return wasDowngraded;
		}
	}

	/**
	 * 將圖片重新編碼，消除潛在的惡意 metadata。
	 * <p>
	 * 動態 GIF 等多幀來源會被降級為第一幀並 {@code log.warn}； 若需要明確得知是否降級，請改用
	 * {@link #sanitizeDetailed(InputStream, String)}。
	 * </p>
	 * @param input 原始圖片串流
	 * @param extension 副檔名（jpg, png, gif 等）
	 * @return 消毒後的圖片位元組；若無法處理則回傳 null
	 */
	public byte[] sanitize(InputStream input, String extension) {
		return sanitizeDetailed(input, extension).bytes();
	}

	/**
	 * 與 {@link #sanitize(InputStream, String)} 相同的消毒流程，但回傳 {@link SanitizeResult}
	 * 附帶幀數與降級旗標。
	 */
	public SanitizeResult sanitizeDetailed(InputStream input, String extension) {
		String formatName = mapToFormatName(extension);
		if (formatName == null) {
			log.debug("不支援的圖片格式: {}", extension);
			return new SanitizeResult(null, -1, false, 0);
		}

		byte[] raw;
		try {
			raw = input.readAllBytes();
		}
		catch (IOException e) {
			log.error("讀取圖片串流失敗: {}", e.getMessage());
			return new SanitizeResult(null, -1, false, 0);
		}

		int frames = detectFrameCount(raw, formatName);
		boolean downgraded = frames > 1;
		int framesDropped = downgraded ? frames - 1 : 0;
		if (downgraded) {
			// N-8: 不再靜默 — 至少在 log 留下軌跡
			log.warn("多幀圖片將被降級為第一幀 (extension={}, frames={}, framesDropped={})", extension, frames, framesDropped);
		}

		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(raw));
			if (image == null) {
				log.warn("ImageIO 無法解析圖片 (extension={})", extension);
				return new SanitizeResult(null, frames, downgraded, framesDropped);
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			boolean written = ImageIO.write(image, formatName, baos);
			if (!written) {
				log.warn("ImageIO.write failed for format: {}", formatName);
				return new SanitizeResult(null, frames, downgraded, framesDropped);
			}

			log.debug("Image sanitized: extension={}, outputSize={}, frames={}, framesDropped={}", extension,
					baos.size(), frames, framesDropped);
			return new SanitizeResult(baos.toByteArray(), frames, downgraded, framesDropped);
		}
		catch (IOException e) {
			log.error("圖片消毒失敗: {}", e.getMessage());
			return new SanitizeResult(null, frames, downgraded, framesDropped);
		}
	}

	/**
	 * 偵測來源圖片的幀數。無法判定（或讀取錯誤）時回 {@code -1}，由呼叫端視為單幀。
	 */
	private int detectFrameCount(byte[] raw, String formatName) {
		try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
			if (iis == null) {
				return -1;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName);
			if (!readers.hasNext()) {
				return -1;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis, false, false);
				return reader.getNumImages(true);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | IllegalStateException e) {
			log.debug("偵測幀數失敗 ({}): {}", formatName, e.getMessage());
			return -1;
		}
	}

	private String mapToFormatName(String extension) {
		return switch (extension.toLowerCase()) {
			case "jpg", "jpeg" -> "JPEG";
			case "png" -> "PNG";
			case "gif" -> "GIF";
			case "bmp" -> "BMP";
			case "webp" -> "WEBP";
			default -> null;
		};
	}

}
