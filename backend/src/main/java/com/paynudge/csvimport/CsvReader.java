package com.paynudge.csvimport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Turns an uploaded file into a header and data rows, coping with what spreadsheets really export: a UTF-8 byte order
 * mark, Windows-1252 text, {@code ;} or tab separators, Excel's {@code sep=} first line, quoted cells, blank lines and
 * ragged rows. Row numbers match what the owner sees in their spreadsheet (the header is row 1).
 */
final class CsvReader {

	static final int MAX_ROWS = 5_000;

	private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

	private CsvReader() {
	}

	record CsvRow(int number, List<String> cells) {

		String cell(int column) {
			return (column >= 0 && column < this.cells.size()) ? this.cells.get(column) : "";
		}

	}

	record CsvTable(List<String> headers, List<CsvRow> rows) {
	}

	static CsvTable read(byte[] bytes) {
		if (looksLikeWorkbook(bytes)) {
			throw ImportException.notCsv("This looks like an Excel workbook. In Excel, choose File > Save As > CSV, "
					+ "then upload that file.");
		}
		String text = decode(bytes);
		if (text.isBlank()) {
			throw ImportException.of(ImportException.EMPTY_FILE, "This file is empty.");
		}

		int skippedLines = 0;
		char delimiter;
		String firstLine = text.lines().findFirst().orElse("");
		if (firstLine.length() == 5 && firstLine.regionMatches(true, 0, "sep=", 0, 4)) {
			delimiter = firstLine.charAt(4);
			text = text.substring(text.indexOf('\n') + 1);
			skippedLines = 1;
		}
		else {
			delimiter = detectDelimiter(firstLine);
		}

		List<CSVRecord> records = parse(text, delimiter);
		int headerIndex = 0;
		while (headerIndex < records.size() && isBlank(records.get(headerIndex))) {
			headerIndex++;
		}
		if (headerIndex == records.size()) {
			throw ImportException.of(ImportException.EMPTY_FILE, "This file is empty.");
		}

		List<String> headers = headers(records.get(headerIndex));
		List<CsvRow> rows = new ArrayList<>();
		for (int i = headerIndex + 1; i < records.size(); i++) {
			CSVRecord record = records.get(i);
			if (isBlank(record)) {
				continue;
			}
			if (rows.size() == MAX_ROWS) {
				throw ImportException.of(ImportException.TOO_MANY_ROWS,
						String.format(Locale.ENGLISH, "This file has more than %,d rows. Split it into smaller files.", MAX_ROWS));
			}
			rows.add(new CsvRow(i + 1 + skippedLines, cells(record)));
		}
		if (rows.isEmpty()) {
			throw ImportException.of(ImportException.NO_ROWS, "This file has a header row but no rows under it.");
		}
		return new CsvTable(headers, rows);
	}

	/** XLSX files are zip archives; old XLS files are OLE compound documents. */
	private static boolean looksLikeWorkbook(byte[] bytes) {
		return startsWith(bytes, 0x50, 0x4B, 0x03, 0x04) || startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0);
	}

	private static boolean startsWith(byte[] bytes, int... prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if ((bytes[i] & 0xFF) != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	/** UTF-8 when the bytes are valid UTF-8 (dropping a byte order mark), otherwise Excel's Windows-1252. */
	private static String decode(byte[] bytes) {
		int offset = startsWith(bytes, 0xEF, 0xBB, 0xBF) ? 3 : 0;
		ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(buffer)
				.toString();
		}
		catch (CharacterCodingException ex) {
			return new String(bytes, offset, bytes.length - offset, WINDOWS_1252);
		}
	}

	/** The separator that appears most often in the header line, outside quotes. Comma wins ties. */
	private static char detectDelimiter(String headerLine) {
		Map<Character, Integer> counts = new HashMap<>();
		boolean quoted = false;
		for (char c : headerLine.toCharArray()) {
			if (c == '"') {
				quoted = !quoted;
			}
			else if (!quoted && (c == ',' || c == ';' || c == '\t' || c == '|')) {
				counts.merge(c, 1, Integer::sum);
			}
		}
		char best = ',';
		for (char candidate : new char[] { ';', '\t', '|' }) {
			if (counts.getOrDefault(candidate, 0) > counts.getOrDefault(best, 0)) {
				best = candidate;
			}
		}
		return best;
	}

	private static List<CSVRecord> parse(String text, char delimiter) {
		CSVFormat format = CSVFormat.DEFAULT.builder()
			.setDelimiter(delimiter)
			.setIgnoreEmptyLines(false)
			.setIgnoreSurroundingSpaces(true)
			.setTrim(true)
			.get();
		try (CSVParser parser = CSVParser.parse(text, format)) {
			return parser.getRecords();
		}
		catch (IOException | UncheckedIOException | IllegalStateException ex) {
			throw ImportException.notCsv("We couldn't read this file as CSV. Check for a quote (\") that is "
					+ "opened but never closed.");
		}
	}

	private static List<String> headers(CSVRecord record) {
		List<String> headers = new ArrayList<>();
		Map<String, Integer> seen = new HashMap<>();
		for (int i = 0; i < record.size(); i++) {
			String header = record.get(i).strip().replaceAll("\\s+", " ");
			if (header.isEmpty()) {
				header = "Column " + (i + 1);
			}
			int count = seen.merge(header.toLowerCase(Locale.ROOT), 1, Integer::sum);
			headers.add((count == 1) ? header : header + " (" + count + ")");
		}
		return List.copyOf(headers);
	}

	private static List<String> cells(CSVRecord record) {
		List<String> cells = new ArrayList<>(record.size());
		record.forEach(cells::add);
		return cells;
	}

	private static boolean isBlank(CSVRecord record) {
		for (String value : record) {
			if (!value.isBlank()) {
				return false;
			}
		}
		return true;
	}

}
