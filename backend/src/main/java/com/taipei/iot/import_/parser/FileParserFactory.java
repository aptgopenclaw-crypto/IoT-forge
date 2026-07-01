package com.taipei.iot.import_.parser;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FileParserFactory {

	private final Map<String, FileParser> parserMap;

	public FileParserFactory(List<FileParser> parsers) {
		this.parserMap = parsers.stream().collect(Collectors.toMap(FileParser::supportedExtension, p -> p));
	}

	public FileParser getParser(String extension) {
		FileParser parser = parserMap.get(extension.toLowerCase());
		if (parser == null) {
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT);
		}
		return parser;
	}

}
