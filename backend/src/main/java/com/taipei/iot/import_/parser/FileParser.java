package com.taipei.iot.import_.parser;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FileParser {

	/** 解析輸入流，回傳列資料清單（第一列視為 header，不回傳） */
	List<Map<String, String>> parse(InputStream inputStream);

	/** 回傳此 parser 支援的副檔名（不含 dot） */
	String supportedExtension();

}
